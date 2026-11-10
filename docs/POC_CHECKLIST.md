# Milestone 3 — On-Device Proof of Concept Checklist

This checklist must be completed on **real Android hardware** (ideally a 4 GB class device) before the generation engine is considered functional.

## Prerequisites

- [ ] Android Studio project builds and installs
- [ ] Hardware assessment (Milestone 2) runs and reports a profile
- [ ] Sufficient free storage (≥ 2 GB recommended for first model pack)

## Model preparation

- [ ] Obtain MobileI2V (or CineMobile) weights / conversion scripts
- [ ] Convert to ONNX (or LiteRT) with documented operator set
- [ ] Quantise (INT8 / FP16 as appropriate)
- [ ] Record exact file sizes and SHA-256 checksums
- [ ] Place files under app-controlled storage (`FRAME/Models/<model_id>/`)

## Runtime integration

- [ ] Add ONNX Runtime Mobile (or LiteRT) dependency
- [ ] Implement `InferenceRuntime` for the chosen engine
- [ ] Load session successfully without crash
- [ ] Confirm input / output tensor names and shapes match the model

## Inference

- [ ] Preprocess a real camera/gallery image to the required resolution & format
- [ ] Run denoising / generation loop
- [ ] Obtain actual frame tensors or bitmaps (not placeholders)
- [ ] Measure wall-clock time and approximate peak memory
- [ ] Observe thermal behaviour; confirm the process can be cancelled

## Encoding

- [ ] Encode frames to H.264 (or supported codec) via MediaCodec / Media3
- [ ] Produce a valid MP4 that plays in the system Gallery / VLC
- [ ] Verify duration, resolution and frame count match expectations

## Documentation

- [ ] Update `docs/MODEL_EVALUATION.md` with measured numbers
- [ ] Change model status from RESEARCH → EXPERIMENTAL or VALIDATED
- [ ] Record known limitations (resolution, duration, devices that OOM)

## Failure handling

If the first model OOMs or fails to convert:

- [ ] Document the exact failure
- [ ] Try CineMobile or a smaller quantised variant
- [ ] Consider a staged T2I → I2V path only after pure I2V is understood
- [ ] Do **not** mark the engine as working

---

**Success definition**  
A user can pick an image, run generation offline, and receive a playable MP4 that was produced by local inference on that device.
