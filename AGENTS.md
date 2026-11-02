# AGENTS.md

Creator's Kit is a RuneLite plugin (Java/Gradle, single module). This file is the fast path;
`CLAUDE.md` has the deeper architecture and hard-won camera/colour notes.

## Build, run, verify

- Windows shell: `.\gradlew.bat compileJava`, `.\gradlew.bat build`. JAR appears at
  `build/libs/CreatorsKit-<version>.jar`.
- `.\gradlew.bat runClient` compiles the test source set and launches the RuneLite dev client with
  this plugin sideloaded: main `com.creatorskit.CreatorsPluginTest`, assertions on (`-ea`), 2 GB heap.
  Same as `.idea/runConfigurations/CreatorsPluginTest.xml`.
- `run-client.bat` builds then calls `runClient`, and labels the console with the current git branch.
  `run-ck-sax-branch.bat` clones/updates the Saxerpillar fork and runs it.
- No unit tests exist. `src/test` only contains the dev-client launcher; `.\gradlew.bat test` validates
  nothing. Verification is compile + launch the client.
- No lint/formatter/typecheck config. `compileJava` is the gate.
- Toolchain: Gradle wrapper 7.4, `sourceCompatibility = '1.9'`. Do not introduce post-Java-9 language
  features. The current local runtime is JDK 18.
- `net.runelite:cache` is deliberately `implementation`, not `compileOnly`: the Cache Searcher reads
  definitions from the local cache and RuneLite's `client.jar` does not ship that module. Do not
  demote it.
- `createProperties` writes `build/resources/main/version.txt`; `classes` depends on it. Do not edit
  generated build output.

## RuneLite dependency drift (1.13 model-copy change)

- `build.gradle` sets `def runeLiteVersion = 'latest.release'`, so RuneLite API changes arrive on the
  next build with no source change. Check the resolved version:
  `.\gradlew.bat dependencies --configuration compileClasspath`
- RuneLite 1.13 removed the rendered-model `Client.mergeModels(Model...)` /
  `mergeModels(Model[], int)` overloads; only `ModelData` overloads remain. The old calls were in
  `programming/ColourTint` and `ProjectileCKObject`.
- Fixed by `models/ModelSources`: model-creation paths register the unlit `ModelData` + lighting
  recipe. `CKObject.ensurePrivateBaseModel()` rebuilds a privately owned base when a per-CKObject
  effect needs mutable arrays; `applyTransformations` supplies a vertex-cloned frame for pitch/tint.
- If you add a model-creation path used by a Character, spotanim, or projectile, register its unlit
  source or colour/pitch effects will be skipped for that model. Use `ModelSources.loadCacheModel`
  for raw cache model IDs; avoid plain `client.loadModel` on the tint/pitch paths.
- Do not treat `applyTransformations` as a general retained copy: the API documents the returned
  model as shared scratch. Vertices are cloned, but colours and other fields share references. The
  private-base step above is what makes per-Character mutation safe.
- `1.12.38` was the last verified pre-1.13 compile target. Pinning is an emergency stopgap, not a
  substitute for the ModelSources migration.

## Architecture

- `com.creatorskit.CreatorsPlugin` — entrypoint; wires config, overlays, camera lock/orb/shake,
  keybinds, and owns the global keyframe store.
- `CKObject` / `ProjectileCKObject` — renderable wrappers. `CKObject.getModel()` runs the animation
  pipeline and may mutate only the per-frame output; `baseModel` can be shared by many Characters.
  `ProjectileCKObject` adds per-frame pitch.
- `Character` — scene actor; owns per-type keyframe tracks, JAU look-at math, and the `CKObject`s.
- `programming/` — runtime: `Programmer` path engine, movement/orientation, animation controller,
  colour tint, sounds, scene overlays.
- `swing/` — Swing UI. `CreatorsPanel`, `ToolBoxFrame`; `swing/timesheet/` is the timeline +
  attributes; `swing/anvil/` is the model-merge tool; `swing/manager/` is the object tree.
- `models/` — cache→model pipeline and OBJ/glTF exporters. `cache/` — local cache readers.
  `saves/` — persistence DTOs. `selection/` — selection manager.
- Keyframes: base `KeyFrame` = `{KeyFrameType, double tick}`. Slots live in `KeyFrameType` and are
  copied in `KeyFrame.createCopy`. `KeyFrameType.isGlobal()` is the source of truth: CAMERA,
  SCREEN_FADE, SCREEN_SHAKE, SOUND_1–4 are global; everything else is per-Character.
- Adding a keyframe type usually touches `KeyFrameType`, `KeyFrame.createCopy`, `AttributePanel` /
  `AttributeSheet`, a `*Attributes` editor, and save DTOs. Do not add only one side.

## Non-obvious constraints

- Camera angles are JAU14 (16384/circle); actor/model orientation is JAU11 (2048/circle).
  `CameraEase.radiansToJau` must use 16384. Do not change the 2048 constants in `Orientation`,
  `Rotation`, or `Programmer`.
- Camera focal-point APIs are `float`; setters require free-camera mode 1 or throw
  `IllegalArgumentException`. Capture free-cam pitch/yaw with `getCameraFpPitch` / `getCameraFpYaw`
  (radians).
- Camera tick order is: undo prior shake → camera-lock lerp → camera keyframe → apply shake. Read the
  camera notes in `CLAUDE.md` before editing this pipeline.
- Swing mutations must be on the EDT (`SwingUtilities.invokeLater`); client access must be on the
  client thread (`clientThread.invokeLater`). Do not show modal dialogs on the client thread — this
  has frozen the client before.
- Colour changes must never mutate a shared `Model`. Work on the per-frame output / a private copy,
  or the tint leaks across Characters. Preserve pristine face-color baselines.
- Movement/keyframe times are `double`. Guard fractional speeds so they do not floor to zero and
  freeze movement.
- UI copy follows `STRINGS_STYLE.md`: tooltip = imperative "what"; the "why" belongs in help buttons.

## Repo conventions

- Commit locally after each revision. Never push unsolicited. `interactive` is the local integration
  branch; features go in sibling `../ck-<feature>` worktrees.
- Remotes: `origin` = `ScreteMonge/creators-kit` (upstream); `creators-kit` = the Saxerpillar fork.
  Check before pushing.
- Confirm the intended version before release: `build.gradle` says `2.1.9`, the plugin descriptor says
  `(DEV)`, and `changelog_v3.0.0.md` exists.
- `README.md` is the user guide; `CLAUDE.md` is the detailed project brain. Read both before changing
  architecture or visual behavior.
