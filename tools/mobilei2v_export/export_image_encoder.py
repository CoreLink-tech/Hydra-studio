"""Export LTX-Video VAE *encoder* (single-frame condition image -> latent) to ONNX.

I2V needs one encode of the reference image. Full LTX VAE is ~1.7 GB on disk; we
export encoder only and run it once per generation. Decoder is Turbo-VAED (separate).

Weights: Lightricks/LTX-Video subfolder=vae (diffusers AutoencoderKLLTXVideo).

Scaling: config scaling_factor (MobileI2V uses ~0.41407 in yaml; LTX config may say 1.0).
We apply the factor passed on the CLI so the app can match training.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import time

import numpy as np
import torch
import torch.nn as nn


class EncoderWrapper(nn.Module):
    """Single RGB image [1,3,H,W] -> latent [1,128,1,h,w] with scaling."""

    def __init__(self, vae, scale_factor: float):
        super().__init__()
        self.vae = vae
        self.scale_factor = scale_factor

    def forward(self, image: torch.Tensor) -> torch.Tensor:
        # AutoencoderKLLTXVideo.encode expects [B,C,F,H,W] video tensors.
        if image.dim() == 4:
            image = image.unsqueeze(2)  # [B,C,1,H,W]
        dist = self.vae.encode(image).latent_dist
        z = dist.mode()  # deterministic for export
        return z * self.scale_factor


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--vae-dir", required=True, help="Path to LTX VAE (diffusers folder or HF cache)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--height", type=int, default=352)
    ap.add_argument("--width", type=int, default=640)
    ap.add_argument("--scale-factor", type=float, default=0.41407,
                    help="Match MobileI2V config.vae.scale_factor")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    if a.height % 32 or a.width % 32:
        raise SystemExit("height/width must be multiples of 32")

    from diffusers import AutoencoderKLLTXVideo

    vae = AutoencoderKLLTXVideo.from_pretrained(a.vae_dir, torch_dtype=torch.float32).eval()
    # Drop decoder weights from memory for export footprint reporting
    if hasattr(vae, "decoder"):
        del vae.decoder
    wrapper = EncoderWrapper(vae, a.scale_factor).eval()

    torch.manual_seed(0)
    image = torch.randn(1, 3, a.height, a.width)
    with torch.no_grad():
        ref = wrapper(image)
    print(f"encoder out shape: {tuple(ref.shape)}")

    tag = f"{a.height}x{a.width}"
    path = os.path.join(a.out, f"ltx_image_encoder_{tag}.onnx")
    t0 = time.time()
    kw = dict(
        input_names=["image"],
        output_names=["latent"],
        opset_version=17,
        do_constant_folding=False,
    )
    try:
        torch.onnx.export(wrapper, image, path, dynamo=False, **kw)
    except TypeError:
        torch.onnx.export(wrapper, image, path, **kw)
    export_s = time.time() - t0

    import onnxruntime as ort

    so = ort.SessionOptions()
    so.intra_op_num_threads = os.cpu_count() or 2
    sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    feed = {"image": image.numpy()}
    got = sess.run(None, feed)[0]
    max_diff = float(np.abs(ref.numpy() - got).max())

    times = []
    for _ in range(3):
        s = time.time()
        sess.run(None, feed)
        times.append(time.time() - s)

    enc_params = sum(p.numel() for p in wrapper.parameters())
    summary = {
        "component": "ltx_image_encoder",
        "params_millions": round(enc_params / 1e6, 2),
        "scale_factor": a.scale_factor,
        "input_shape": list(image.shape),
        "latent_shape": list(ref.shape),
        "onnx_file": os.path.basename(path),
        "onnx_mb": round(os.path.getsize(path) / 1e6, 1),
        "onnx_sha256": sha256(path),
        "export_seconds": round(export_s, 1),
        "ort_inputs": [(i.name, i.shape, i.type) for i in sess.get_inputs()],
        "ort_outputs": [(o.name, o.shape, o.type) for o in sess.get_outputs()],
        "max_abs_diff_ort_vs_pytorch": max_diff,
        "one_encode_seconds_this_machine": [round(t, 3) for t in times],
        "cpu_threads_used": so.intra_op_num_threads,
        "torch": torch.__version__,
        "onnxruntime": ort.__version__,
        "note": "Encoder only (decoder deleted before export). Full LTX VAE on disk is larger.",
    }
    out_json = os.path.join(a.out, f"summary_image_encoder_{tag}.json")
    with open(out_json, "w") as f:
        json.dump(summary, f, indent=2)
    print(json.dumps(summary, indent=2))
    if max_diff > 1e-3:
        raise SystemExit(f"ORT vs PyTorch max abs diff too high: {max_diff}")


if __name__ == "__main__":
    main()
