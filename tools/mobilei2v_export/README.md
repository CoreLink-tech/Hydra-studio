# MobileI2V → ONNX feasibility (research tooling, not part of the app)

Answers: can MobileI2V components export to ONNX and run on CPU with ONNX Runtime,
and do the official weights fit the code?

## Components

| Script | Component | Weights |
|--------|-----------|---------|
| `export_dit.py` | Diffusion transformer (267M) | `hustvl/MobileI2V` → `hybrid_371.pth` |
| `export_vae_decoder.py` | Turbo-VAED mobile decoder | `hustvl/Turbo-VAED` → `Turbo-VAED-LTX.pth` |
| `export_image_encoder.py` | LTX VAE encoder (single frame) | `Lightricks/LTX-Video` → `vae/` |

## Run

GitHub → Actions → **MobileI2V ONNX feasibility** → Run workflow (or push to `poc/mobilei2v-onnx`).

Three parallel jobs + a summarise job. Read the run Summary page. ONNX files upload only if `upload_onnx` is ticked.

## Findings so far (DiT, real weights, 352×640)

- Export works. ORT matches PyTorch (~5e-6).
- Official checkpoint loads; only `pos_embed` is shape-skipped at non-720p (expected).
- **No text cross-attention** in any of the 16 blocks (`SanaBlock_cross` / `SanaBlock_vanila` have it commented out). Prompt does not condition the DiT. ONNX graph has no `text_embeds` input.
- ~1.16 s / DiT forward on a 4-thread GitHub runner CPU (not a phone).
- DiT ONNX ~1061 MB.

## Product implication

- I2V = image latent only (plus timestep / flow score).
- T2V = text → first frame (small T2I) then I2V; on-device Qwen text encoder is optional for motion.

## Not done yet

- End-to-end sample video on CPU
- Phone timing / quality
- Android integration

## Local notes

- DiT export peaks ~6 GB RAM (workflow adds swap).
- Height/width should be multiples of 32; frames = 1+8k (17 default).
