# FRAME — QA & Release Readiness (Milestone 11)

**Version tag**: `0.1.0-milestone11`  
**Date**: 2026-11-10  
**Scope**: Source-complete studio through Milestone 11. On-device AI inference remains gated on real model weights.

---

## 1. Feature completeness (source)

| Area | Status | Notes |
|------|--------|-------|
| Project foundation (Compose, Hilt, Room, nav) | Done | |
| Hardware assessment | Done | Real ActivityManager / PowerManager APIs |
| Model research + runtime scaffolding | Done | ONNX Runtime Mobile wired |
| Generation queue, progress, cancel | Done | Single-flight; honest failures |
| Home / projects / film / scenes | Done | Room-backed |
| Timeline editor (trim/split/tracks) | Done | Non-destructive |
| Audio + titles on timeline | Done | Volume + fade hints |
| Export (Media3 Transformer) | Done | Fails if media missing |
| Text-to-video staged pipeline | Done | Orchestration only until weights |
| Model library UI + storage | Done | Install state, compatibility warnings |
| Settings / cache clear | Done | |

---

## 2. QA checklist (run on device)

### Crash & stability
- [ ] Cold start → Home
- [ ] Create project → open Film → add scene → open Editor → Export screen
- [ ] Rotate (if supported) / background / resume during generation task
- [ ] Cancel generation while QUEUED and while RUNNING

### Memory
- [ ] Hardware screen reports plausible total/available RAM
- [ ] Generation refuses or fails clearly under low-memory flag
- [ ] No unbounded bitmap retention after Create / Editor leave

### Storage
- [ ] `FRAME/` directories created under app files
- [ ] Settings “Clear cache” reduces cache without deleting projects
- [ ] Models screen storage counter updates after refresh

### Thermal
- [ ] ResourceMonitor maps PowerManager thermal status
- [ ] Severe/critical path blocks new generation with message

### Offline
- [ ] Airplane mode: Home, Projects, Editor, Settings usable
- [ ] Generation fails with model-not-installed (expected) — not with network errors
- [ ] Export fails only for missing media, not network

### Export
- [ ] Empty timeline → clear failure
- [ ] Timeline with missing file paths → clear failure
- [ ] With real media files → playable MP4 in exports folder
- [ ] Output opens in system player / VLC

### Low-end (≈4 GB)
- [ ] App installs and navigates without OOM on launch
- [ ] Profile classified Balanced or Low-memory
- [ ] Large models show compatibility warnings

---

## 3. Known limitations (release notes)

1. **No packaged AI weights** — I2V/T2V tasks fail until models are converted and installed under `FRAME/Models/`.
2. **Image picker** on Create is not fully bound to SAF in all paths — path can be injected for tests.
3. **Preview monitor** is a placeholder until Media3 player is bound to timeline media.
4. **Destructive Room migrations** during development (`fallbackToDestructiveMigration`) — ship proper migrations before production store release.
5. **T2V** depends on validated T2I + I2V; not claimed working.

---

## 4. Release readiness verdict

| Criterion | Verdict |
|-----------|---------|
| Architecture complete for offline film studio | **Yes** |
| No fake AI generation | **Yes** |
| Ready for Play Store as “AI video generator” | **No** — requires successful device PoC + weights |
| Ready for internal / collaborator Android Studio builds | **Yes** |
| Ready for model integration sprint | **Yes** |

**Recommended next engineering sprint**  
1. Android Studio build on physical 4 GB device  
2. Convert MobileI2V → ONNX, place under Models  
3. Bind tensor names in `OnDeviceVideoGenerator`  
4. One successful I2V MP4  
5. Then claim generation in product copy  

---

## 5. Build

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Requires Android SDK / Android Studio on the build machine.

---

*FRAME — offline film studio. Measure before claiming.*
