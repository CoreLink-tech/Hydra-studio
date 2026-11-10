# Export timeline fidelity (Step 2)

## Applied

| Timeline field | Export behaviour |
|----------------|------------------|
| `sourceInMs` / `sourceOutMs` | `MediaItem.ClippingConfiguration` on each video/audio item |
| `volume` | `ChannelMixingAudioProcessor` constant-gain matrix |
| Item order | Sorted by `startMs` within video and audio sequences |
| Split clips | Each segment keeps its own source range (no full-file re-export) |

## Media3 1.5.0 limitation — gaps / startMs offsets

`EditedMediaItemSequence` **only concatenates** items back-to-back. There is no API to place an item at an absolute `startMs` or to insert silence/black for gaps.

**Current behaviour:** if `ExportTimelinePlanner` detects a gap &gt; 50 ms between consecutive video or audio items, export **fails** with an explicit message (does not silently close the gap).

**Proposed alternatives (future):**
1. Generate short black/silent filler media files for each gap and insert them into the sequence.
2. Upgrade Media3 when/if Transformer gains composition-time offsets.
3. Pre-render a continuous intermediate timeline with MediaCodec before Transformer.

## Titles / subtitles

TEXT items are **not** burned in. Export fails listing the TEXT item labels. Overlay effects can be added later.

## Manual device checks

See `ExportTimelinePlannerTest` KDoc for trim, split, gap, and volume scenarios.
