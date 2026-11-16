"""Export the MobileI2V diffusion transformer (DiT) to ONNX and validate it on CPU.

What this does
  1. Imports the official MobileI2V model code WITHOUT its heavy training deps
     (mmcv, deepspeed, triton, ...) by stubbing the package __init__ files.
  2. Builds the 300M DiT, loads the official checkpoint (or random weights for a
     graph-only dry run) and wraps it for batch 1 with fixed latent size.
  3. Exports to ONNX, then checks ONNX Runtime (CPU) against PyTorch.
  4. Writes summary.json with real measurements. Nothing here is estimated.

Deviations from the official forward pass (all deliberate, all reported):
  * Token count is derived from the latent size. The official code hardcodes 2760
    (the 720p size) in `timestep.repeat(1, 2760)`.
  * Text mask is not used. At batch 1 the official path drops padded tokens
    (masked_select), so feeding only the real tokens is the same computation.
    The text length is a dynamic axis; the app must send the unpadded tokens.
  * Classifier-free guidance is NOT inside the graph. The app runs the graph twice
    (prompt, empty prompt) and combines the results, same as FlowEuler does.
"""
import argparse
import hashlib
import json
import os
import sys
import time
import types

import numpy as np
import torch
import torch.nn as nn


SOURCE_PATCHES = [
    # (file, exact old text, new text, why)
    ("diffusion/model/nets/sana_blocks.py",
     "attn = self.attn_drop(attn).half()",
     "attn = self.attn_drop(attn).to(v.dtype)",
     "official code hardcodes fp16 here (GPU-only); keep the tensor's own dtype"),
]


def apply_source_patches(repo):
    """Exact-match edits to the cloned upstream code. Fails loudly if upstream changed."""
    applied = []
    for rel, old, new, why in SOURCE_PATCHES:
        path = os.path.join(repo, rel)
        src = open(path).read()
        if new in src:
            applied.append(why + " (already applied)")
            continue
        assert src.count(old) == 1, f"patch target not found exactly once in {rel}: {old!r}"
        open(path, "w").write(src.replace(old, new))
        applied.append(why)
    return applied


PATCHES = []
NAMES = ["latent", "timestep", "text_embeds", "flow_score"]


def stub_packages(repo):
    PATCHES[:] = apply_source_patches(repo)
    """Make `diffusion.*` importable without executing the package __init__ files."""
    for name, rel in [
        ("diffusion", "diffusion"),
        ("diffusion.model", "diffusion/model"),
        ("diffusion.model.nets", "diffusion/model/nets"),
        ("diffusion.utils", "diffusion/utils"),
    ]:
        mod = types.ModuleType(name)
        mod.__path__ = [os.path.join(repo, rel)]
        sys.modules[name] = mod

    class Registry:
        def __init__(self, name):
            self.name, self._d = name, {}

        def register_module(self, name=None, **_):
            def deco(obj):
                self._d[name or obj.__name__] = obj
                return obj
            return deco

        def get(self, key):
            return self._d[key]

    # mmcv is a training-time dependency that does not build on modern Python. The
    # model code only touches it for logging and distributed helpers, never for maths.
    mmcv = types.ModuleType("mmcv")
    runner = types.ModuleType("mmcv.runner")
    runner.get_dist_info = lambda: (0, 1)
    mutils = types.ModuleType("mmcv.utils")
    mlog = types.ModuleType("mmcv.utils.logging")
    mlog.logger_initialized = {}
    mmcv.runner, mmcv.utils, mutils.logging = runner, mutils, mlog
    sys.modules.update({"mmcv": mmcv, "mmcv.runner": runner,
                        "mmcv.utils": mutils, "mmcv.utils.logging": mlog})

    builder = types.ModuleType("diffusion.model.builder")
    builder.MODELS = Registry("models")
    sys.modules["diffusion.model.builder"] = builder
    sys.path.insert(0, repo)
    os.environ["DISABLE_XFORMERS"] = "1"


class DiTWrapper(nn.Module):
    """Batch-1 wrapper with explicit tensors in/out and no python-list masks."""

    def __init__(self, m, T, H, W):
        super().__init__()
        self.m, self.T, self.H, self.W = m, T, H, W
        n = T * H * W
        cond = torch.zeros(1, n)
        cond[:, : H * W] = 1.0  # first latent frame = the reference image
        self.register_buffer("cond_mask", cond)

    def forward(self, x, timestep, y, flow_score):
        from einops import rearrange

        m, T, H, W = self.m, self.T, self.H, self.W
        S, N = H * W, T * H * W
        x = rearrange(x, "B C T H W -> (B T) C H W")
        x = m.x_embedder(x)
        x = rearrange(x, "(B T) S C -> B (T S) C", B=1, T=T, S=S)

        ts = timestep.unsqueeze(1).repeat(1, N)
        ts = torch.min(ts, (1.0 - self.cond_mask) * 1000.0) / 1000.0
        t = m.t_embedder(ts)
        t0 = m.t_block(t)

        fs = flow_score.unsqueeze(1).repeat(1, N)
        fs = m.flow_block(m.flow_embedder(fs))

        y = m.y_embedder(y, False)
        if m.y_norm:
            y = m.attention_y_norm(y)
        y = y.squeeze(1).view(1, -1, x.shape[-1])

        for block in m.blocks:
            x = block(x, y, t0, fs, None, H, W, T, S)
        x = m.final_layer(x, t)
        return m.unpatchify(x, T, H, W, T, H, W)


def patch_rope_grid(model, T, H, W):
    """The official LiteLA picks the rotary grid from a hardcoded list of token counts
    (660, 1530, 2640, 6120, else 23x40). Force the grid that matches our latent size."""
    n = 0
    for mod in model.modules():
        pg = getattr(mod, "position_getter", None)
        if pg is None:
            continue
        orig = pg
        mod.position_getter = lambda b, t=None, h=None, w=None, device=None, _o=orig: _o(b, t=T, h=H, w=W, device=device)
        n += 1
    return n


def official_forward(model, x, ts, y, fs, T, H, W):
    """Run the UNMODIFIED official Mobiledit.forward (only the xformers-style list mask
    is dropped, which is a no-op at batch 1). Valid only for 720x1280 because the
    official forward hardcodes 2760 tokens."""
    import diffusion.model.nets.sana_blocks as sb

    if not getattr(sb.MultiHeadCrossAttention, "_patched", False):
        orig = sb.MultiHeadCrossAttention.forward
        sb.MultiHeadCrossAttention.forward = lambda self, x, cond, mask=None: orig(self, x, cond, None)
        sb.MultiHeadCrossAttention._patched = True
    cond = torch.zeros(1, T * H * W)
    cond[:, : H * W] = 1.0
    return model(x, ts, None, y, cond, fs, mask=None)


def safe_load(path, max_rounds=30):
    """Load a checkpoint with weights_only=True (no arbitrary code execution).

    Official checkpoints often carry numpy metadata that the safe loader rejects by
    default. We allow numpy types ONLY, one at a time, as the loader reports them.
    Any other kind of object stops the run so a human can decide.
    """
    import importlib
    import pickle
    import re

    allowed = []
    for _ in range(max_rounds):
        try:
            with torch.serialization.safe_globals(allowed):
                return torch.load(path, map_location="cpu", weights_only=True), allowed
        except pickle.UnpicklingError as e:
            # PyTorch words this two ways depending on the object kind.
            m = (re.search(r"Unsupported global: GLOBAL ([\w\.]+) was not an allowed global", str(e))
                 or re.search(r"but got <class '([\w\.]+)'>", str(e)))
            if not m:
                raise
            mod, _, name = m.group(1).rpartition(".")
            if not (mod == "numpy" or mod.startswith("numpy.")):
                raise RuntimeError(
                    f"Checkpoint needs a non-numpy object ({m.group(1)}). Refusing to allow it "
                    "automatically; inspect the file and decide.") from e
            allowed.append(getattr(importlib.import_module(mod), name))
    raise RuntimeError("Checkpoint needed too many allow-list rounds; stopping.")


def build_model(repo, latent_h, latent_w, weights):
    stub_packages(repo)
    # Force the pure-PyTorch code path. The Triton kernels are CUDA-only and the
    # config (attn_type=linear, ffn_type=glumbconv) does not need them.
    import diffusion.utils.import_utils as iu
    iu.is_triton_module_available = lambda: False
    from diffusion.model.nets.mobiledit import Mobiledit_300M_P1_D16

    model = Mobiledit_300M_P1_D16(
        input_height=latent_h, input_width=latent_w, in_channels=128,
        caption_channels=896, model_max_length=300, qk_norm=True, y_norm=True,
        y_norm_scale_factor=0.01, attn_type="linear", ffn_type="glumbconv",
        mlp_ratio=2.5, mlp_acts=["silu", "silu", None], use_pe=False,
        linear_head_dim=32, pred_sigma=False, learn_sigma=False,
        class_dropout_prob=0.0, cross_norm=False, micro_condition=False,
    )
    info = {"weights": "random (graph check only, NOT real model output)"}
    if weights != "random":
        ckpt, allowed = safe_load(weights)
        top_keys = list(ckpt.keys())[:20] if isinstance(ckpt, dict) else type(ckpt).__name__
        sd = ckpt.get("state_dict", ckpt)
        sd = {k.removeprefix("module."): v for k, v in sd.items()}
        non_tensor = [k for k, v in sd.items() if not torch.is_tensor(v)]
        sd = {k: v for k, v in sd.items() if torch.is_tensor(v)}

        # Official hybrid_371.pth was trained at 720x1280 (latent 23x40 → 920
        # tokens). Mobile target sizes (e.g. 352x640 → 11x20 → 220 tokens) make
        # resolution-dependent tensors like pos_embed shape-mismatch. strict=False
        # still raises on size mismatch for keys present in both, so drop those
        # tensors and keep the rest. Model uses RoPE (use_pe=False); pos_embed is
        # not required for a valid graph / weight check at other resolutions.
        model_sd = model.state_dict()
        shape_mismatch = []
        filtered = {}
        for k, v in sd.items():
            if k in model_sd and tuple(model_sd[k].shape) != tuple(v.shape):
                shape_mismatch.append({
                    "key": k,
                    "checkpoint": list(v.shape),
                    "model": list(model_sd[k].shape),
                })
                continue
            filtered[k] = v

        res = model.load_state_dict(filtered, strict=False)
        info = {
            "checkpoint_top_level_keys": top_keys,
            "non_tensor_entries_ignored": non_tensor[:20],
            "numpy_globals_allowed": [f"{o.__module__}.{o.__name__}" for o in allowed],
            "weights": os.path.basename(weights),
            "latent_hw": [latent_h, latent_w],
            "token_count": latent_h * latent_w,
            "shape_mismatched_keys_skipped": shape_mismatch,
            "shape_mismatch_count": len(shape_mismatch),
            "missing_keys": list(res.missing_keys)[:20],
            "unexpected_keys": list(res.unexpected_keys)[:20],
            "missing_count": len(res.missing_keys),
            "unexpected_count": len(res.unexpected_keys),
            "note": (
                "Shape-mismatched tensors (usually pos_embed) were skipped so "
                "mobile resolutions can load the 720x1280-trained checkpoint. "
                "For bit-exact weight match use --height 720 --width 1280."
            ),
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
    ap.add_argument("--repo", required=True, help="path to cloned hustvl/MobileI2V")
    ap.add_argument("--weights", default="random")
    ap.add_argument("--out", required=True)
    ap.add_argument("--height", type=int, default=720)
    ap.add_argument("--width", type=int, default=1280)
    ap.add_argument("--frames", type=int, default=17)
    ap.add_argument("--skip-export", action="store_true", help="forward + fidelity check only")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)

    lh, lw = -(-a.height // 32), -(-a.width // 32)  # ceil; decoder crops the padding
    T = (a.frames - 1) // 8 + 1
    model, winfo = build_model(a.repo, lh, lw, a.weights)
    params = sum(p.numel() for p in model.parameters())
    n_rope = patch_rope_grid(model, T, lh, lw)
    wrapper = DiTWrapper(model, T, lh, lw).eval()

    torch.manual_seed(0)
    ex = (
        torch.randn(1, 128, T, lh, lw),
        torch.tensor([500.0]),
        torch.randn(1, 1, 24, 896),
        torch.tensor([1.0]),
    )
    with torch.no_grad():
        ref = wrapper(*ex)
    assert ref.shape == (1, 128, T, lh, lw), f"unexpected output shape {tuple(ref.shape)}"

    fidelity = "skipped: official forward hardcodes 2760 tokens, only valid at 720x1280"
    if (lh, lw) == (23, 40):
        with torch.no_grad():
            off = official_forward(model, *ex[:1], ex[1], ex[2], ex[3], T, lh, lw)
        fidelity = {"max_abs_diff_wrapper_vs_official_forward": float((off - ref).abs().max()),
                    "ref_abs_mean": float(ref.abs().mean())}

    tag = f"{a.height}x{a.width}"
    if a.skip_export:
        part = {"latent_shape": [1, 128, T, lh, lw], "params_millions": round(params / 1e6, 1),
                "weights": winfo, "rope_modules_patched": n_rope, "source_patches": PATCHES,
                "wrapper_vs_official": fidelity}
        json.dump(part, open(os.path.join(a.out, f"forward_only_{tag}.json"), "w"), indent=2)
        print(json.dumps(part, indent=2))
        return

    path = os.path.join(a.out, f"mobilei2v_dit_{tag}.onnx")
    kw = dict(
        input_names=NAMES,
        output_names=["velocity"],
        dynamic_axes={"text_embeds": {2: "text_len"}},
        opset_version=17,
        do_constant_folding=False,  # exporter folding exhausted 7 GB RAM; ORT folds at load
    )
    t0 = time.time()
    try:
        torch.onnx.export(wrapper, ex, path, dynamo=False, **kw)
    except TypeError:  # older torch without the dynamo flag
        torch.onnx.export(wrapper, ex, path, **kw)
    export_s = time.time() - t0

    import onnxruntime as ort

    so = ort.SessionOptions()
    so.intra_op_num_threads = os.cpu_count() or 2
    sess = ort.InferenceSession(path, so, providers=["CPUExecutionProvider"])

    used = {i.name for i in sess.get_inputs()}
    text_in_graph = "text_embeds" in used
    checks = {}
    for L in (24, 7, 120):  # different text lengths prove the dynamic axis works
        inp = (ex[0], ex[1], torch.randn(1, 1, L, 896), ex[3])
        with torch.no_grad():
            want = wrapper(*inp).numpy()
        feed = {n: t.numpy() for n, t in zip(NAMES, inp) if n in used}
        got = sess.run(None, feed)[0]
        checks[f"text_len_{L}"] = {
            "max_abs_diff": float(np.abs(want - got).max()),
            "ref_abs_mean": float(np.abs(want).mean()),
        }

    feed = {n: t.numpy() for n, t in zip(NAMES, ex) if n in used}
    times = []
    for _ in range(3):
        s = time.time()
        sess.run(None, feed)
        times.append(time.time() - s)

    summary = {
        "params_millions": round(params / 1e6, 1),
        "weights": winfo,
        "latent_shape": [1, 128, T, lh, lw],
        "text_conditioning_reaches_graph": text_in_graph,
        "block_classes": {type(b).__name__: sum(type(x) is type(b) for x in model.blocks) for b in model.blocks},
        "blocks_with_text_cross_attn": sum(hasattr(b, "cross_attn") for b in model.blocks),
        "rope_modules_patched": n_rope,
        "source_patches": PATCHES,
        "wrapper_vs_official": fidelity,
        "onnx_file": os.path.basename(path),
        "onnx_mb": round(os.path.getsize(path) / 1e6, 1),
        "onnx_sha256": sha256(path),
        "export_seconds": round(export_s, 1),
        "ort_inputs": [(i.name, i.shape, i.type) for i in sess.get_inputs()],
        "ort_outputs": [(o.name, o.shape, o.type) for o in sess.get_outputs()],
        "numeric_checks_onnx_vs_pytorch": checks,
        "one_forward_pass_seconds_this_machine": [round(t, 2) for t in times],
        "cpu_threads_used": so.intra_op_num_threads,
        "torch": torch.__version__,
        "onnxruntime": ort.__version__,
        "note": "Timing is for the machine that ran this script, not a phone.",
    }
    with open(os.path.join(a.out, f"summary_{tag}.json"), "w") as f:
        json.dump(summary, f, indent=2)
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
