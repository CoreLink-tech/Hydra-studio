# Text-to-Video Pipeline (Milestone 9)

## Strategy

FRAME treats **image-to-video as the primary generation path**.  
Text-to-video is implemented as a **staged pipeline**:

```
Prompt
  → Stage A: Text-to-Image (small local T2I model)
  → intermediate still (PNG in cache)
  → Stage B: Image-to-Video (MobileI2V / CineMobile)
  → MP4
```

This is more realistic on mid-range Android than a single end-to-end video diffusion model.

## Current status

| Stage | Status |
|-------|--------|
| Pipeline orchestration (`TextToVideoPipeline`) | Implemented |
| Queue support (`TEXT_TO_VIDEO` tasks) | Implemented |
| Create UI enqueue | Implemented |
| Validated on-device T2I weights | **Not present** |
| Validated on-device I2V weights | **Not present** (Milestone 3 device PoC) |
| End-to-end success on device | **Blocked** |

When models are missing, the task fails with:

`MODEL_NOT_INSTALLED` — clear message, no fake video.

## Catalogue

- `sd_turbo_t2i` — research placeholder for Stage A  
- `mobilei2v_270m` / `cinemobile` — Stage B candidates  

Statuses remain **RESEARCH** until real-device validation.

## Enabling T2V for real

1. Complete I2V device PoC (`docs/POC_CHECKLIST.md`).
2. Select a licence-cleared, quantised T2I model that fits the target profile.
3. Convert to ONNX / LiteRT; record checksums.
4. Bind T2I session in `TextToVideoPipeline.runTextToImage`.
5. Feed the still into `OnDeviceVideoGenerator`.
6. Measure RAM, time, thermal on baseline hardware.
7. Flip model status to VALIDATED only after success.

## Hardware note

4 GB devices will struggle with both stages in memory. Expect:

- Sequential load/unload of T2I then I2V  
- 512×512 stills  
- Short clips  
- Aggressive cancellation on thermal severity  

---

*No simulated generation. T2V claims require measured on-device success.*
