"""Export Turbo-VAED-LTX decoder to ONNX and validate on CPU with ONNX Runtime.

Mobile path: DiT produces video latents; this decoder turns them into RGB frames.
Teacher LTX decoder is NOT used here — Turbo-VAED is the mobile-oriented distill.

Inputs
  latents: float32 [1, 128, T, H, W]  where T=(frames-1)//8+1, H=height//32, W=width//32
Outputs
  video:   float32 [1, 3, frames, height, width]  (approx; spatial may include pad crop)

Nothing estimated: export + numeric check use real Turbo-VAED-LTX weights.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import time

import numpy as np
import torch

from turbo_vae import TurboVAEDecoder


def load_config(path):
    with open(path) as f:
        return json.load(f)


def safe_load_ckpt(path):
    """weights_only with numpy allow-list (same pattern as export_dit)."""
    import importlib
    import pickle
    import re

    allowed = []
    for _ in range(40):
        try:
            with torch.serialization.safe_globals(allowed):
                return torch.load(path, map_location="cpu", weights_only=True)
        except pickle.UnpicklingError as e:
            m = (re.search(r"Unsupported global: GLOBAL ([\w\.]+) was not an allowed global", str(e))
                 or re.search(r"but got <class '([\w\.]+)'>", str(e)))
            if not m:
                raise
            mod, _, name = m.group(1).rpartition(".")
            if not (mod == "numpy" or mod.startswith("numpy.")):
                raise RuntimeError(f"Refusing non-numpy global {m.group(1)}") from e
            allowed.append(getattr(importlib.import_module(mod), name))
    raise RuntimeError("Too many allow-list rounds")


def load_decoder(config_path, weights_path):
    model = TurboVAEDecoder(load_config(config_path))
    raw = safe_load_ckpt(weights_path)
    if isinstance(raw, dict):
        raw = raw.get("Turbo-VAED-LTX", raw.get("state_dict", raw))
    weights = {
        k: v for k, v in raw.items()
        if k not in {"latents_mean", "latents_std"}
        and not str(k).startswith("aligned_feature_projection_heads.")
        and torch.is_tensor(v)
    }
    # strip common prefixes
    cleaned = {}
    for k, v in weights.items():
        nk = k
        for p in ("module.", "decoder."):
            if nk.startswith(p):
                nk = nk[len(p):]
        cleaned[nk] = v
    res = model.load_state_dict(cleaned, strict=False)
    info = {
        "weights": os.path.basename(weights_path),
        "missing_count": len(res.missing_keys),
        "unexpected_count": len(res.unexpected_keys),
        "missing_keys": list(res.missing_keys)[:30],
        "unexpected_keys": list(res.unexpected_keys)[:30],
        "tensor_keys_loaded": len(cleaned),
    }
    return model.float().eval(), info


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--config", default=os.path.join(os.path.dirname(__file__), "turbo_vae.json"))
    ap.add_argument("--weights", required=True, help="Turbo-VAED-LTX.pth (or .safetensors)")
    ap.add_argument("--out", required=True)
    ap.add_argument("--height", type=int, default=352)
    ap.add_argument("--width", type=int, default=640)
    ap.add_argument("--frames", type=int, default=17)
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    if a.height % 32 or a.width % 32:
        raise SystemExit("height/width must be multiples of 32")
    if a.frames < 1 or (a.frames - 1) % 8:
        raise SystemExit("frames must be 1+8k (1, 9, 17, 33, ...)")

    lh, lw = a.height // 32, a.width // 32
    T = (a.frames - 1) // 8 + 1

    model, winfo = load_decoder(a.config, a.weights)
    params = sum(p.numel() for p in model.parameters())

    torch.manual_seed(0)
    latent = torch.randn(1, 128, T, lh, lw)
    with torch.no_grad():
        ref = model(latent)
    out_shape = tuple(ref.shape)
    expected_min = (1, 3, a.frames, a.height, a.width)
    # Upsamplers may produce slightly different temporal length; record actual.
    print(f"decoder out shape: {out_shape} (requested frames={a.frames}, HxW={a.height}x{a.width})")

    tag = f"{a.height}x{a.width}_f{a.frames}"
    path = os.path.join(a.out, f"turbo_vaed_decoder_{tag}.onnx")
    t0 = time.time()
    kw = dict(
        input_names=["latents"],
        output_names=["video"],
        opset_version=17,
        do_constant_folding=False,
    )
    try:
        torch.onnx.export(model, latent, path, dynamo=False, **kw)
    except TypeError:
        torch.onnx.export(model, latent, path, **kw)
    export_s = time.time() - t0

    import onnxruntime as ort

    so = ort.SessionOptions()
    so.intra_op_num_threads = os.cpu_count() or 2
    sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])
    feed = {"latents": latent.numpy()}
    got = sess.run(None, feed)[0]
    max_diff = float(np.abs(ref.numpy() - got).max())

    times = []
    for _ in range(3):
        s = time.time()
        sess.run(None, feed)
        times.append(time.time() - s)

    summary = {
        "component": "turbo_vaed_decoder",
        "params_millions": round(params / 1e6, 2),
        "weights": winfo,
        "latent_shape": list(latent.shape),
        "output_shape": list(out_shape),
        "requested_video_shape": list(expected_min),
        "onnx_file": os.path.basename(path),
        "onnx_mb": round(os.path.getsize(path) / 1e6, 1),
        "onnx_sha256": sha256(path),
        "export_seconds": round(export_s, 1),
        "ort_inputs": [(i.name, i.shape, i.type) for i in sess.get_inputs()],
        "ort_outputs": [(o.name, o.shape, o.type) for o in sess.get_outputs()],
        "max_abs_diff_ort_vs_pytorch": max_diff,
        "one_decode_seconds_this_machine": [round(t, 3) for t in times],
        "cpu_threads_used": so.intra_op_num_threads,
        "torch": torch.__version__,
        "onnxruntime": ort.__version__,
        "note": "Timing is for the machine that ran this script, not a phone.",
    }
    out_json = os.path.join(a.out, f"summary_vae_decoder_{tag}.json")
    with open(out_json, "w") as f:
        json.dump(summary, f, indent=2)
    print(json.dumps(summary, indent=2))
    if max_diff > 1e-3:
        raise SystemExit(f"ORT vs PyTorch max abs diff too high: {max_diff}")


if __name__ == "__main__":
    main()
