# FRAME — Offline AI Film Studio

**Native Android (Kotlin + Jetpack Compose)** application for offline AI video generation and film production.

> **Status: Milestones 1–11 source-complete** (`0.1.2`)  
> App version **0.1.2** (versionCode 12). On-device AI generation requires validated model weights (see `docs/POC_CHECKLIST.md`).

## What is FRAME?

FRAME lets users generate video clips with AI **on-device**, organise them into scenes, edit timelines, add audio/text, and export finished films. No mandatory cloud generation.

Target: Android phones from ~4 GB RAM (baseline: Galaxy A07 class).

## Architecture (high level)

```
ui/           Compose screens (Home, Create, Generation, Projects, Film, Editor, Export, Models, Hardware, Settings)
ai/           Model catalogue, ONNX runtime, generation queue, T2V pipeline
editor/       Timeline repository (non-destructive trim/split)
export/       Media3 Transformer export engine
data/         Room (projects, scenes, clips, timeline, tasks, audio, export)
hardware/     Device profiler + thermal/memory gates
core/files/   FRAME/Projects, Models, Cache, Logs layout
```

## Tech stack

- Kotlin, Jetpack Compose, Material 3, Navigation
- Hilt, Room, DataStore
- Media3 (player + Transformer)
- ONNX Runtime Mobile
- WorkManager (dependency ready)

## Design system

From the HTML prototype (visual reference only):

| Token | Hex |
|-------|-----|
| Background | `#111214` |
| Panel | `#151619` / `#191B1F` |
| Accent | `#C0A47E` |
| Text primary | `#E9E8E4` |

## Milestones

| # | Milestone | Status |
|---|-----------|--------|
| 1 | Project foundation | Done |
| 2 | Hardware assessment | Done |
| 3 | Model research + PoC source | Done |
| 4 | Generation engine | Done |
| 5 | Home + project management | Done |
| 6 | Video editor | Done |
| 7 | Audio + text | Done |
| 8 | Export engine | Done |
| 9 | Text-to-video pipeline | Done |
| 10 | Model library + optimisation | Done |
| 11 | Final QA + release docs | Done |

## Docs

- `docs/MODEL_EVALUATION.md` — model candidates
- `docs/POC_CHECKLIST.md` — device PoC gate
- `docs/MILESTONE_3_REPORT.md` — inference scaffolding report
- `docs/T2V_PIPELINE.md` — staged T2I→I2V plan
- `docs/QA_AND_RELEASE.md` — QA checklist + limitations

## Build

Requires Android Studio / Android SDK (not available in pure source environments):

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Rules

- **No simulated AI generation.** Tasks fail clearly until models are installed and validated.
- Hardware numbers come from system APIs.
- Source media is never rewritten by the timeline (non-destructive edits).
- `index.html` is design reference only — the product is the native app.

## Licence / models

Application code: follow repository licence.  
AI weights: verify upstream licences before redistribution (see model catalogue entries).
