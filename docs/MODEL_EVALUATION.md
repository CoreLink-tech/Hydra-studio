# FRAME — Model Evaluation Report (Milestone 3)

**Date**: 2026-11-10  
**Target baseline device**: Samsung Galaxy A07-class (~4 GB RAM, Android 16)  
**Primary generation mode for PoC**: Image-to-Video (I2V)

This document records the research and selection process for on-device video generation models.  
No model is marked “compatible” until actual on-device inference and valid MP4 output have been verified.

---

## 1. Evaluation Criteria

For every candidate we require answers to:

1. Publicly available weights?
2. Licence allows redistribution and commercial use?
3. Demonstrated or strongly evidenced Android / mobile deployment?
4. Storage footprint (quantised)?
5. Peak RAM estimate on mobile?
6. Supported resolution / frames / duration?
7. Text conditioning and/or image conditioning?
8. Conversion path to a mobile runtime (ONNX, LiteRT, etc.)?
9. Realistic generation time on mid-range / low-end hardware?

---

## 2. Candidate Summary (November 2026)

### 2.1 MobileI2V (HUST) — Primary candidate

| Attribute | Value |
|-----------|--------|
| Type | Image-to-Video diffusion |
| Parameters | ~270 M |
| Paper | arXiv:2511.21475 (Nov 2025) |
| Code | https://github.com/hustvl/MobileI2V (public) |
| Key claims | 17-frame 720p in ~2.2 s on high-end mobile (iPhone 16 class) after 2-step distillation; linear hybrid attention |
| Quantisation | Research provides path; community ONNX ports exist |
| Licence | Check repository (research code; verify redistribution) |
| Android evidence | Community projects (e.g. MobileI2V Console) have demonstrated ONNX Runtime Mobile integration |
| Estimated storage | Expect 300–800 MB rangeised (to be measured) |
| Peak RAM (est.) | Challenging on 4 GB; likely needs sequential loading + low resolution first |
| Recommendation | **Primary PoC target** |

**Risks**  
- Peak memory on 4 GB devices unknown until measured.  
- Official mobile demo focused on iOS-class hardware.  
- Conversion quality (PyTorch → ONNX / LiteRT) must be validated.

### 2.2 CineMobile

| Attribute | Value |
|-----------|--------|
| Type | I2V focused on cinematic camera motion |
| Footprint | < 1 GB after hybrid PTQ (claimed) |
| Peak memory | ~1.8 GB reported on Dimensity 8400 |
| Frames | 49-frame 480p demonstrated |
| Strength | Explicitly designed for mobile, strong quantisation story |
| Weakness | Newer; Android integration maturity and licence need verification |
| Recommendation | Strong secondary candidate if MobileI2V memory is too high |

### 2.3 SnapGen-V (Snap Research)

| Attribute | Value |
|-----------|--------|
| Type | Text-to-Video (UNet-based) |
| Parameters | 0.6 B |
| Claim | 5 s video in ~5 s on iPhone 16 Pro Max |
| Android | Primarily demonstrated on Apple Silicon; Android port maturity lower |
| Recommendation | Monitor; not first choice for Android PoC |

### 2.4 Wan 2.1 / 2.2 Light (1.3 B / 5 B)

| Attribute | Value |
|-----------|--------|
| Type | T2V + I2V |
| Licence | Apache 2.0 (favourable) |
| Reality on phone | Practical on-device requires ~8–12 GB+ RAM for usable performance |
| Recommendation | Only for High-performance profile later; unsuitable as 4 GB baseline PoC |

### 2.5 Auxiliary / fallback approaches

- Very small motion / frame-interpolation models  
- Staged pipeline: text → image (small SD-Turbo / similar) → I2V  
- These may be needed if pure video diffusion cannot fit on the baseline device.

---

## 3. Selected Runtime Strategy

**Primary runtime**: ONNX Runtime Mobile  
**Secondary**: LiteRT (Google) where a model converts cleanly to `.tflite`

Reasons for ONNX Runtime Mobile first:
- Most research models originate in PyTorch.
- Existing community Android ports of MobileI2V use ONNX.
- Good quantisation and execution-provider options (CPU, GPU, QNN where available).

The architecture will keep the runtime behind an interface so a second runtime can be added without rewriting the generation pipeline.

---

## 4. Hardware Reality Check (from Milestone 2)

| Profile | Approx. RAM | Expected generation constraints |
|---------|-------------|----------------------------------|
| Low-memory | < 3.5 GB | Only the smallest quantised models, 3–5 s clips, strict sequential loading |
| Balanced (baseline) | ~4 GB | Conservative models, short clips, one task at a time |
| Performance | ~6 GB | Larger models after validation |
| High-performance | ≥ 8 GB | More aggressive settings possible |

**Galaxy A07-class devices will almost certainly require**:
- Generation at 320–512 px (not native 720p)
- Short clips (12–24 frames)
- Aggressive unload of text encoder / VAE / denoiser stages
- Continuous memory and thermal monitoring

---

## 5. Proof-of-Concept Plan (what “success” means)

A successful Milestone 3 PoC must demonstrate **on a real device**:

1. Model files present and integrity-checked.
2. Model session loads without crash.
3. A real input image is preprocessed to the model’s expected shape.
4. Inference produces actual frame tensors / images (not placeholders).
5. Frames are encoded into a playable MP4 via MediaCodec / Media3.
6. The MP4 plays in the system player and in FRAME.
7. Peak memory, generation time, and thermal behaviour are recorded.
8. The process can be cancelled safely.

Until the above is measured on hardware, the engine is **not** claimed to work.

---

## 6. Decision for FRAME (November 2026)

| Priority | Model | Reason |
|----------|--------|--------|
| 1 | MobileI2V | Smallest serious mobile I2V research model with public code and community Android interest |
| 2 | CineMobile | Strong mobile quantisation claims; backup if MobileI2V memory is excessive |
| 3 | Staged T2I → I2V | Fallback path for text-to-video later |

**Immediate engineering tasks**
1. Scaffold model metadata, download, and validation layer.
2. Define `InferenceRuntime` and `VideoGenerator` interfaces.
3. Add ONNX Runtime Mobile dependency (when building on a machine with network + Android SDK).
4. Create a minimal Android instrumentation / device test harness for the PoC.
5. Convert / obtain a quantised MobileI2V (or CineMobile) package and measure on real 4 GB hardware.

---

## 7. Honest Limitations (current knowledge)

- No open model currently guarantees comfortable 720p I2V on 4 GB RAM devices.
- Generation times on mid-range and low-end phones will be measured in tens of seconds to minutes, not real-time.
- Thermal throttling and battery drain will be significant; the app must pause or reduce workload when thermal status is elevated.
- Model conversion (especially attention variants) can break operators; each conversion must be validated.

This report will be updated after the first real-device measurement.

---

*FRAME does not ship simulated generation. Only validated local inference counts as success.*
