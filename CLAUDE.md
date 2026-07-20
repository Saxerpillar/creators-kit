# Creator's Kit — project brain

> Bootstrapped 2026-07-20. This committed file is the shared context every Claude
> instance / worktree loads (personal memory does not travel between worktrees).
> Keep the **Status** section current; write durable facts here, not only in memory.

## What this is

A RuneLite creator/cinematic toolkit: spawn & merge models from the game cache, animate
them, program movement paths, drive the camera, and author **keyframed scenes on a timeline**.
Exports models (OBJ/glTF). Main plugin class `com.creatorskit.CreatorsPlugin`
(`@PluginDescriptor name="Creator's Kit (DEV)"`, implements `MouseListener`). ~191 source
files under `com.creatorskit`. Author: ScreteMonge.

It is also the **visual/animation authority** for sibling projects (e.g. Drakan Gatekeep):
lift CK's overlay recipes, sprite tables, model-merge and freeze-frame patterns rather than
hand-rolling or guessing game visuals.

## Standing constraints (must persist)

- **Commit locally after each revision. NEVER push unsolicited.** `interactive` is the local
  integration branch (stays in the main checkout, never pushed); features get their own
  worktree (`../ck-<feature>`). Currently the main checkout tracks `main`.
- **Reuse proven patterns** — copy the working lifecycle, don't reinvent it.
- **UI strings follow `STRINGS_STYLE.md`**: tooltip = imperative "what, not why"; the "why"
  lives in help buttons. Grep that file's anti-patterns before writing config/tooltip copy.

## Build

- Plain `java` gradle plugin. Group `com.creatorssuite`. **Gradle wrapper 7.4**,
  `sourceCompatibility = 1.9`, Lombok 1.18.20, UTF-8.
- **RuneLite dep is pinned to `latest.release`** (`def runeLiteVersion = 'latest.release'`):
  `compileOnly net.runelite:client`, `implementation net.runelite:cache` (bundled for the
  Cache Searcher, excludes commons-cli + jna). Repos: mavenLocal → repo.runelite.net → central.
  ⚠️ Because it floats to latest, **RuneLite API breaks land silently on the next build** —
  this is how the camera float/JAU14 changes arrived (see gotchas). Resolved API sources live in
  `~/.gradle/caches/.../runelite-api-<ver>-sources.jar` — read them to confirm signatures/units.
- No distZip/shadow/application task. `createProperties` writes `version.txt` into resources.
- ⚠️ **Version drift**: `build.gradle` says `2.1.9`, descriptor is "(DEV)", but a
  `changelog_v3.0.0.md` exists. Confirm the intended version before documenting/releasing.

## Architecture (packages under `com.creatorskit`)

- **(root)** `CreatorsPlugin` (wires config, overlays, camera lock/orb/shake, keybinds, and the
  central store for GLOBAL keyframes), `Character` (a scene actor; owns per-type keyframe tracks +
  JAU look-at math), `CKObject`/`ProjectileCKObject` (renderable/animatable model wrappers;
  freeze/animation-frame logic), `CreatorsConfig`, `CreatorsOverlay`.
- **`programming/`** — runtime behaviour: `Programmer` (path engine), `MovementManager`/
  `MovementComposition`/`PathFinder`/`Coordinate`/`Direction`, `CKAnimationController` (frame-range +
  freeze), `ColourController`/`ColourTint`/`ColourBlendMode`, `SoundController`, scene overlays
  (Text/Overhead/Health/Hitsplat/Bar/BossHealth/ScreenFade). `programming/orientation/` — `Orientation`
  angle conversions + `OrientationAction`/`Goal`/`Instruction`.
- **`swing/`** — UI. `CreatorsPanel` (main side panel), `ToolBoxFrame`/`ParentPanel` (tool windows),
  `ModelOrganizer`, `TransmogPanel`, `CacheSearcherTab`.
  - `swing/anvil/` — `ModelAnvil` (cache-model merge/transform tool), `ComplexPanel`, `GroupPanel`.
  - `swing/manager/` — object tree (drag-drop).
  - `swing/timesheet/` — the timeline: `TimeSheetPanel`, `AttributePanel` (live-camera capture at
    `captureLiveCameraIntoSpinners`). Subpackages: `keyframe/` (all KeyFrame types + `CameraEase`/
    `CameraEaseType`/`CustomEasingCurve`, `KeyFrameType`), `keyframe/keyframeactions/`,
    `keyframe/settings/` (sprite/toggle enums), `attributes/` (one `*Attributes` editor per kf type +
    `CurveEditorDialog`), `sheets/`, `blocks/`.
- **`models/`** — cache→model pipeline: `CustomModel`, `DetailedModel`, `KitRecolourer`,
  `ModelUtilities`/`ModelImporter`/`ModelGetter`, `CustomLighting`. `models/exporters/` (OBJ, glTF).
  `models/datatypes/` (cache def records: NPCData/ItemData/ObjectData/SeqData/…).
- **`cache/`** — local cache reading (`parser/CacheLoader`, `metadata/CacheMetadataStore`).
- **`saves/`** — persistence DTOs (`SetupSave`, `CharacterSave`, `GlobalKeyFrames`, …).
- **`selection/`** — `SelectionManager`.

**KeyFrame model**: base `KeyFrame` = `{KeyFrameType, double tick}`; ~29 slots enumerated in
`KeyFrameType` and copied in `KeyFrame.createCopy`. Subclasses: Movement, Animation, Orientation,
Spawn, Model, SpotAnim (SPOTANIM & SPOTANIM2), Text, Overhead, Health, Hitsplat (1–4), Projectile,
Shield, Special, Colour, ScreenFade, ScreenShake, Camera, Sound (area 1–4 + per-character).
**Local vs Global**: `KeyFrameType.isGlobal()` → CAMERA, SCREEN_FADE, SCREEN_SHAKE, SOUND_1–4 are
global (own AttributeSheet + central store on the plugin); everything else is per-Character.

## Key gotchas (from code + hard-won debugging)

- **Camera angles are JAU14 (16384/circle); actor/model orientation is JAU11 (2048/circle).**
  RuneLite migrated the *camera* angle unit from JAU11→JAU14: `getCameraYaw/Pitch` now index
  `Perspective.SINE14` (16384-entry table), and `setCameraYawTarget/PitchTarget` consume JAU14.
  `CameraEase.radiansToJau` must convert with `JAU_PER_CIRCLE = 16384` (a keyframe applied with the
  old 2048 factor comes out ~8× under-rotated → the view snaps). **Do NOT touch the 2048 constants in
  `Orientation`/`Rotation`/`Programmer`** — actors still use S=0/W=512/N=1024/E=1536, full circle 2048.
  Sibling features still reading `getCameraYaw/Pitch` as JAU11 (manual-rotate hotkeys, camera-relative
  object placement via `Rotation.getJagexDegrees`) remain to be corrected.
- **Camera focal-point API is float** (runelite-api 1.12.31): `get/setCameraFocalPointX/Y/Z` and
  `getCameraFpX/Y/Z`, `getCameraFpPitch/Yaw` are all `float`. Setters **require free-camera mode 1**
  or they throw `IllegalArgumentException`. Capture free-cam pitch/yaw via `getCameraFpPitch/FpYaw`
  (radians — scale-independent); the JAU conversion only happens at apply time.
- **Camera pipeline order per tick**: undo previous shake offset → camera-lock lerp → camera keyframe
  → apply shake. Camera-lock captures the (focal − character) offset and lerps the focal each tick;
  the **Oculus orb hijacks focal-point updates**, so the orb is disabled while locked. Screen-shake
  adds a per-tick offset to the focal (subtracting the prior tick's first) so the lock target stays
  the un-shaken focal. Axes: focal X/Z = ground plane, Y = height.
- **Freeze-frame / animation** (`CKAnimationController`, `CKObject`, `Character`): custom
  `AnimationController` with `firstFrameOverride`/`lastFrameOverride` (0 = natural), `pauseTicks` dwell
  before loop. `CKObject.setAnimationFrame(..., allowFreeze)` freezes on the last frame; `Character`
  resets `setAnimationRange(0,0,0)` + `setFreeze(false)` between runs to clear a stale freeze.
- **Colour recolour NEVER mutates a shared Model.** `ColourController` installs a per-CKObject
  `ColourTint` and post-processes the *animated per-frame output* via `client.mergeModels` on a clone,
  tinting face colours. Two bugs this avoids: (a) replacing baseModel with a merged clone loses bone
  data → `animate()` stops deforming; (b) writing a shared baseModel leaks colour across Characters.
  Pristine face-colour arrays are captured once per envelope, re-captured when a Model keyframe swaps
  baseModel mid-envelope.
- **Sub-tick timing**: keyframes are keyed by `double tick`. Guard fractional speeds (e.g. 0.5) so
  they don't floor to 0 and freeze movement (`CKObject` ~line 305).

## Status (2026-07-20)

- On `main`, clean. Camera keyframe **JAU14 fix committed** (`da2e8ac`): `CameraEase.radiansToJau`
  → base 16384 + `% 16384`, shortest-arc threshold → 8192, dropped a stale `% 2047` on yaw apply, and
  fixed the screen-shake yaw unit. Compiles clean; **awaiting in-client confirmation** (capture a
  camera keyframe, scrub back, verify the view holds instead of snapping).
- **Open follow-ups from the same JAU14 change**: manual camera-rotate hotkeys and camera-relative
  object placement (`Rotation.getJagexDegrees`) still assume JAU11.

## Related

- Ports **mlgudi/keyframe-camera** for the camera keyframe engine (same `radiansToJau`, `CAMERA_DO_ZOOM`,
  focal-point apply). That reference repo is stale (2024, pre-migration) — it would break the same way
  on a current client, so don't treat it as up-to-date for the API.
- Docs in repo root: `README.md` (user guide), `STRINGS_STYLE.md` (UI copy rules), `changelog_v3.0.0.md`.
- Deob/mirror clients (jbx5 / devious-client) for engine behaviour; resolved runelite-api sources jar
  in the gradle cache for exact signatures/units.
