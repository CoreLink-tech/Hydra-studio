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


def fix_onnx_transpose_perms(path: str) -> int:
    """Rewrite negative axes in Transpose perm attributes to positive indices.

    Legacy torch.onnx.export can emit perm={0,-1,1,2,3} from channel-last
    reshapes inside LTX ResNet blocks. ONNX Runtime rejects negative perms.
    """
    import onnx
    from onnx import helper

    model = onnx.load(path)
    fixed = 0
    for node in model.graph.node:
        if node.op_type != "Transpose":
            continue
        for attr in node.attribute:
            if attr.name != "perm" or not attr.ints:
                continue
            perm = list(attr.ints)
            rank = len(perm)
            new_perm = [p + rank if p < 0 else p for p in perm]
            if new_perm == perm:
                continue
            if sorted(new_perm) != list(range(rank)):
                # Not a simple negative rewrite; leave alone
                continue
            del attr.ints[:]
            attr.ints.extend(new_perm)
            fixed += 1
    if fixed:
        # Clear value_info that may carry rank-0 placeholders from bad inference
        del model.graph.value_info[:]
        onnx.save(model, path)
    return fixed


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
    # Export with explicit 5-D video tensor so the graph never invents
    # unsqueeze/rank-ambiguous transposes at the boundary.
    video = torch.randn(1, 3, 1, a.height, a.width)

    class VideoEncoderWrapper(nn.Module):
        def __init__(self, inner, scale):
            super().__init__()
            self.vae = inner.vae if hasattr(inner, "vae") else inner
            self.scale_factor = scale

        def forward(self, video_bcthw: torch.Tensor) -> torch.Tensor:
            dist = self.vae.encode(video_bcthw).latent_dist
            return dist.mode() * self.scale_factor

    vwrap = VideoEncoderWrapper(wrapper, a.scale_factor).eval()
    with torch.no_grad():
        ref = vwrap(video)
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
    # Prefer dynamo exporter when available; fall back to legacy.
    exported = False
    for dynamo in (True, False):
        try:
            torch.onnx.export(vwrap, video, path, dynamo=dynamo, **kw)
            exported = True
            print(f"exported with dynamo={dynamo}")
            break
        except Exception as e:
            print(f"dynamo={dynamo} failed: {type(e).__name__}: {e}")
    if not exported:
        raise SystemExit("ONNX export failed with both dynamo and legacy exporters")
    export_s = time.time() - t0

    n_fixed = fix_onnx_transpose_perms(path)
    print(f"fixed {n_fixed} Transpose perm attributes with negative axes")

    import onnxruntime as ort

    so = ort.SessionOptions()
    so.intra_op_num_threads = os.cpu_count() or 2
    try:
        sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    except Exception as e:
        # Last resort: write summary with failure detail and re-raise
        summary = {
            "component": "ltx_image_encoder",
            "status": "ort_load_failed",
            "error": str(e),
            "transpose_perms_fixed": n_fixed,
            "latent_shape": list(ref.shape),
            "onnx_mb": round(os.path.getsize(path) / 1e6, 1),
            "onnx_file": os.path.basename(path),
            "note": "Graph exported but ORT refused to load it after perm rewrite.",
        }
        out_json = os.path.join(a.out, f"summary_image_encoder_{tag}.json")
        with open(out_json, "w") as f:
            json.dump(summary, f, indent=2)
        print(json.dumps(summary, indent=2))
        raise

    feed = {"image": video.numpy()}
    got = sess.run(None, feed)[0]
    max_diff = float(np.abs(ref.numpy() - got).max())

    times = []
    for _ in range(3):
        s = time.time()
        sess.run(None, feed)
        times.append(time.time() - s)

    enc_params = sum(p.numel() for p in vwrap.parameters())
    summary = {
        "component": "ltx_image_encoder",
        "status": "ok",
        "params_millions": round(enc_params / 1e6, 2),
        "scale_factor": a.scale_factor,
        "input_shape": list(video.shape),
        "latent_shape": list(ref.shape),
        "transpose_perms_fixed": n_fixed,
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
        "note": "Encoder only. Input is [1,3,1,H,W] (single-frame video). Decoder is Turbo-VAED.",
    }
    out_json = os.path.join(a.out, f"summary_image_encoder_{tag}.json")
    with open(out_json, "w") as f:
        json.dump(summary, f, indent=2)
    print(json.dumps(summary, indent=2))
    if max_diff > 1e-3:
        raise SystemExit(f"ORT vs PyTorch max abs diff too high: {max_diff}")


if __name__ == "__main__":
    main()
