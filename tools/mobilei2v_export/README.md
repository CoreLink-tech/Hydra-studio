# MobileI2V -> ONNX feasibility test (research tooling, not part of the app)

Question this answers: can the MobileI2V diffusion transformer be exported to ONNX and run
on CPU with ONNX Runtime, and does the official checkpoint actually fit the code?

Scope today: **the DiT only.** The text encoder (Qwen2-0.5B), the LTX image encoder and the
Turbo-VAED decoder are NOT converted yet. Nothing here produces a video.

## Run it
GitHub -> Actions -> "MobileI2V ONNX feasibility" -> Run workflow (or push to this branch).
Read the run's Summary page. The ONNX file is only uploaded if you tick `upload_onnx`.

## Verified locally (random weights, graph mechanics only)
- Export works. ONNX Runtime output matches PyTorch within 4e-6.
- Wrapper output equals the unmodified official forward pass at 720x1280 (diff 0.0).
- 352x640, 17 frames: 1.7 s per forward pass on 2 CPU threads (x86 server, NOT a phone).
- All 16 blocks lack a text cross-attention layer, so the prompt cannot affect the output.

## NOT verified until the workflow runs with real weights
- That the official checkpoint loads without missing/unexpected keys.
- Anything about output quality.

## Deliberate deviations from the official code (all printed in summary.json)
1. Hardcoded `.half()` in attention replaced by the tensor's own dtype (GPU-only cast).
2. Rotary grid forced to the real latent size (official code picks it from a fixed list).
3. Token count derived from latent size (official code hardcodes 2760).
4. Guidance is not inside the graph; the app would run it twice and combine.
5. Triton/mmcv stubbed. They are training/GPU-only and not used by this config.
