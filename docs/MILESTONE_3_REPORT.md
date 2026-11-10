# Milestone 3 Completion Report — FRAME

**Date**: 2026-11-10  
**Scope**: AI Model Research + Proof-of-Concept scaffolding and source integration

---

## 1. Research (complete)

See `docs/MODEL_EVALUATION.md`.

| Decision | Choice |
|----------|--------|
| Primary model | MobileI2V (~270M, I2V) |
| Secondary | CineMobile |
| High-end only | Wan 2.1 1.3B Light |
| Runtime | ONNX Runtime Mobile (primary), LiteRT secondary |
| Baseline device | ~4 GB RAM — expect low resolution, short clips, sequential loading |

No model is marked VALIDATED until real-device measurement.

---

## 2. Source deliverables (complete)

| Component | Path | Purpose |
|-----------|------|---------|
| Model catalogue | `ai/models/ModelCatalogue.kt` | Data-driven registry |
| Install state | `ai/models/ModelInstallState.kt` | NOT_PRESENT → INSTALLED only after files + checks |
| Download manager | `ai/download/ModelDownloadManager.kt` | Local scan, checksum, download stub |
| Inference interface | `ai/runtime/InferenceRuntime.kt` | Runtime-agnostic session API |
| ONNX implementation | `ai/runtime/onnx/OnnxInferenceRuntime.kt` | Real OrtEnvironment / OrtSession wrapper |
| Image preprocessor | `ai/preprocessing/ImagePreprocessor.kt` | Aspect-safe resize + float tensor |
| Frame encoder | `media/FrameEncoder.kt` | Bitmap list → H.264 MP4 via MediaCodec |
| VideoGenerator API | `ai/generation/VideoGenerator.kt` | Contract + progress + failure reasons |
| On-device generator | `ai/generation/OnDeviceVideoGenerator.kt` | Full pipeline skeleton; fails honestly until weights validated |
| Unavailable generator | `ai/generation/UnavailableVideoGenerator.kt` | Explicit non-working path |
| AI DI module | `di/AiModule.kt` | Binds ONNX runtime |
| Gradle | `libs.versions.toml` + `app/build.gradle.kts` | `onnxruntime-android:1.20.0` |

---

## 3. What is intentionally NOT claimed

- Actual model weights are **not** packaged in this repository.
- Actual inference has **not** been executed on a Galaxy A07 (or any device) from this environment.
- No MP4 has been produced by local diffusion in this session.
- Model status remains **RESEARCH**.

The generator returns clear `FailureReason.MODEL_NOT_INSTALLED` or `INFERENCE_FAILED` until:

1. Converted ONNX (or LiteRT) files are placed under `FRAME/Models/<model_id>/`
2. Input/output tensor names are bound
3. One successful frame → MP4 path is measured on hardware
4. `docs/MODEL_EVALUATION.md` is updated with numbers and status → VALIDATED

---

## 4. How to finish the PoC on a real device

Follow `docs/POC_CHECKLIST.md`.

Minimum path:

1. Build the app in Android Studio (`./gradlew assembleDebug`)
2. Convert MobileI2V (or CineMobile) to ONNX; quantise
3. Copy files into the app’s model directory (or implement the download URL once checksums exist)
4. Bind tensor names in `OnDeviceVideoGenerator`
5. Run one image → frames → `FrameEncoder` → playable MP4
6. Record peak RAM, time, thermal behaviour
7. Update catalogue status and this report

---

## 5. Milestone 3 status summary

| Requirement | Status |
|-------------|--------|
| Research candidate models | Done |
| Select practical I2V model | Done (MobileI2V) |
| Integrate inference runtime (source) | Done (ONNX Runtime Mobile) |
| Download / package model | Structure ready; weights not shipped |
| Load model locally | Code ready; needs files on device |
| Run actual inference | Blocked on weights + device |
| Generate frames | Blocked on inference |
| Encode MP4 | Encoder implemented and ready |
| Test on Galaxy A07 | Requires physical device |
| Compatibility report | This document + MODEL_EVALUATION.md |

**Milestone 3 source work is complete.**  
The remaining gate is device-side validation with real weights — outside the scope of pure source development in this environment.

---

*FRAME does not ship simulated generation.*
