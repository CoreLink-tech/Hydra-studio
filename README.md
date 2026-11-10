# FRAME — Offline AI Film Studio

**Native Android application** for offline AI video generation and film production.

> Current status: **Milestone 1 — Project Foundation** (in progress)

## What is FRAME?

FRAME lets users generate video clips with AI **entirely on-device**, organise them into scenes, edit timelines, add audio/text, and export finished films. No mandatory cloud generation.

Target: Android phones starting from ~4 GB RAM (baseline validation: Galaxy A07 class).

## Repository Structure

```
Hydra-studio/
├── app/                          # Main Android application module
│   └── src/main/java/com/corelink/frame/
│       ├── ui/                   # Compose UI, theme, screens, navigation
│       ├── data/local/           # Room database, entities, DAOs
│       └── di/                   # Hilt modules
├── index.html                    # Original visual prototype (reference only)
└── README.md
```

## Tech Stack (Milestone 1)

- Kotlin
- Jetpack Compose + Material 3
- Navigation Compose
- Hilt
- Room
- DataStore (ready)
- Media3 (ready for later)
- WorkManager (ready for later)

## Design System

Colours taken directly from the HTML prototype:

| Token         | Hex       |
|---------------|-----------|
| Background    | `#111214` |
| Sidebar/Panel | `#151619` / `#191B1F` |
| Accent        | `#C0A47E` |
| Text Primary  | `#E9E8E4` |
| Text Secondary| `#92949A` |

## Development Milestones

1. **Project Foundation** ← current
2. Hardware Assessment
3. AI Model Research + Proof of Concept (actual inference)
4. Generation Engine
5. Home + Project Management
6. Video Editor
7. Audio + Text
8. Export Engine
9. Text-to-Video
10. Model Library + Optimisation
11. Final QA + Release

## Building

Requirements:
- Android Studio Ladybug or newer (or AGP 8.7+)
- JDK 17
- Android SDK 35

```bash
./gradlew assembleDebug
```

## Important Notes

- The HTML prototype is a **visual reference only**. The real app is native Compose.
- No simulated AI generation will be shipped. Generation only succeeds after real local inference + valid MP4.
- Hardware stats are collected at runtime from Android system APIs.

## Licence

Private / CoreLink-tech. All rights reserved for now.
