# Changelog

All notable changes to BetterController are documented in this file. The format
loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and
the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.3.0] - 2026-10-14

### Fixed
- A button still held when a screen closed acted in the world on the next
  tick: the `B` that closed the inventory or pause menu dropped the held item,
  `A` on *Back to Game* jumped, `RT` / `LT` attacked or used. Buttons held
  across a screen now have to be released once before they act in the world.
  Sneak and sprint still carry over, so letting go at a ledge is not forced.
- The first frame of a screen opened from the world no longer reacts to the
  button that opened it. `DPAD_UP` for chat doubles as menu-up and used to move
  the selection off the chat field; `LT` on a chest paged back.
- The virtual keyboard no longer auto-repeats `A`: holding it on a key typed
  that key over and over.
- The last screen, its widgets and its slots stayed referenced after returning
  to the world. They are released now, and the cursor capture state with them,
  so the next inventory hides the mouse cursor again while the pad drives it.
- With a controller pinned but absent, the `F8` overlay blamed a missing GLFW
  mapping instead of the pin. The 1.20.1 line had the fix; this one missed it.
- `RB` was bound to both pick block and next hotbar slot, so one press picked
  the block and then moved off the slot it had just filled. Pick block is on
  `DPAD_LEFT` now; configs still on the old `["RB"]` are migrated (schema 12).
- Rebinding a menu control did not replace it: the built-in default kept
  answering underneath, so a rebound confirm still fired on `A`, and `RB`
  moved both the tab and the page in the creative inventory. Defaults now
  only apply to a menu action with no readable binding.
- A released trigger rested at 0 instead of GLFW's -1, which reads as half
  pulled - above the default trigger threshold - on an untouched snapshot.
- Saving the config truncated the file before writing it, so a crash or full
  disk mid-save reset every setting. It is written to a temporary file and
  moved into place now. Unrecognised binding names (say `RIGHT_BUMBER`) are
  logged against their action instead of being skipped in silence.
- Elapsed-time checks used the wall clock, so an NTP correction or a manual
  clock change could stall menu repeat or fire a burst of moves. They use a
  monotonic clock now.
- Out-of-range whole numbers in the config fell back to their default while
  decimals were clamped to the nearest bound. Both clamp now; only
  `preferredJoystickIndex` still falls back, since clamping an impossible
  index would pin the mod to an arbitrary pad.
### Changed
- Menu navigation is calmer: the first repeat comes after 300 ms, then one
  step every 170 ms (was 140 / 55 ms, which moved the selection almost every
  tick). Configs still on the old defaults are migrated (schema 11); custom
  values are kept.
- The JAR and the release tag carry the Minecraft version:
  `bettercontroller-0.3.0+26.2.jar`, tag `v0.3.0+26.2`. Every version branch
  ships the same mod version, and the names no longer collide.
- Built with Fabric Loom 1.18.4, Gradle 9.8.1, Fabric Loader 0.19.5 and Fabric
  API 0.161.0. Loom 1.18 runs Gradle on Java 25; players still need only the
  Java their Minecraft version ships with.

### Removed
- Unused code: a config preview method that mutated the live config it was
  meant to leave alone, four other members nothing called, and a Maven
  publication with no repository to publish to.
- `examples/bettercontroller.example.json` still described the pre-0.1.0
  config (per-controller layouts, radial menu, vibration). It is now a copy of
  the shipped defaults.

## [0.2.1] - 2026-08-20

### Fixed
- Analog movement never reached the player. The stick's vector was written
  during the client tick, then `KeyboardInput.tick` rebuilt it from the key
  bindings and normalised it, so every push past the deadzone moved at full
  speed. It is applied at the tail of that method now, and only while the
  stick is actually deflected.
- The sideways axis was mirrored: vanilla treats a positive value as strafing
  left, the stick reports positive to the right.
- Confirming a slider with `A` snapped it to 50%.

## [0.2.0] - 2026-08-18

Supports Minecraft 26.2, which needs Java 25. The 1.21.11 line stays on 0.1.0
and Java 21.

### Added
- Minecraft 26.2 support: a full port to the unobfuscated codebase and Mojang
  names, including the reshaped GUI rendering, which extracts render state
  through `GuiGraphicsExtractor` instead of drawing immediately.
- A pad GLFW has no mapping for is no longer a silent dead end. Drop the device's
  line from SDL_GameControllerDB into `config/bettercontroller-mappings.txt` and
  the mod applies it at startup. When no usable gamepad is found, the log and the
  `F8` overlay both name the connected device and the GUID a mapping needs —
  previously the overlay just said `Connected: false` with nothing to act on.
  The database itself is not bundled: a megabyte of text would dwarf the mod.
- `preferredControllerGuid` / `preferredJoystickIndex` pin an instance to one
  specific gamepad (config schema v10). Two game instances on one machine can now
  each drive their own pad on a LAN world, which is as close to couch co-op as
  Minecraft Java gets. When a preference is set and no pad matches, no controller
  is used at all — a second instance must never steal the first one's pad.
- The `F8` overlay shows the slot and GUID of the active pad, which is what the
  two settings above are configured from. The README had claimed this for a while;
  only the controller name was actually displayed.

### Changed
- The `Controller Settings…` button is positioned relative to the screen's own
  bottom-most widget instead of at fixed coordinates, so it reads as part of the
  vanilla layout at any window size or GUI scale. It no longer floats in the title
  bar of the options screen. The `ControlsOptionsScreen` special case is gone.
- Camera look is now sampled once per rendered frame instead of once per client
  tick, from a Mixin at the head of `GameRenderer.render` — the same point in the
  frame where vanilla applies the mouse, right after `Mouse.tick()` and before the
  frame's camera is built. The right stick used to be read at 20 Hz and the value
  stretched across render frames, adding up to 50 ms of camera latency.
- The frame delta used to integrate stick speed is averaged over a few frames. A
  stick reports a rate, so the angle turned is rate × elapsed time — and the only
  elapsed time available is the previous frame's, which under uneven frame pacing
  turned frame-time noise into visible camera-speed noise. Being a mean, it leaves
  the average turn rate untouched.
- Camera smoothing is frame-rate independent. Smoothing strengths are still
  expressed per client tick, and rescaled to the actual frame delta, so a 144 fps
  client no longer smooths seven times faster than a 30 fps one.
- The look clock uses `System.nanoTime` and clamps the frame delta to 1 ms
  instead of 4 ms, so the camera no longer speeds up above ~250 fps.

### Fixed
- Camera look froze while the HUD was hidden (`F1`). The camera was driven from a
  HUD element, which vanilla skips when the HUD is off; it now runs from the
  renderer. HUD hints and the debug overlay stay on the HUD element.
- The mod shipped without an icon, so it showed up blank in the Fabric mod list.

### Internal
- `InputTranslator.translate` no longer produces look; the frame-paced
  `InputTranslator.updateLook` does. Callers that need the camera value must use
  the latter.
- `ControllerPoller.refreshAxes` re-reads only the analog axes of the already
  detected controller (one GLFW call, no device enumeration, no allocation),
  which is what the per-frame path uses.
- `ControllerSnapshot` caches the `values()` arrays of the button / axis enums
  instead of cloning them on every poll.

## [0.1.0] - Initial public release

First clean release. The mod's scope is intentionally limited to *playing
Minecraft Java with a gamepad*; everything else has been removed.

### Added
- Plug-and-play GLFW controller detection (Xbox, PlayStation, Switch Pro,
  generic) with live connect / disconnect.
- Analog look + movement pipeline with per-axis deadzone, anti-deadzone,
  configurable response curve, and adaptive smoothing.
- Controller-driven GUI navigation across vanilla screens, including:
  - widget navigation (buttons, sliders, list widgets)
  - inventory slot navigation with row/lane aware scoring
  - creative inventory tab cycling (RB / LB)
  - right stick analog scroll for any scrollable screen
  - virtual keyboard for chat / text fields
- In-game settings screen with five sliders (movement deadzone, look X / Y
  sensitivity, look speed multiplier, trigger threshold). Other options live
  in `config/bettercontroller.json`.
- Debug overlay (toggle with `F8`): raw axes, trigger values, glyph set,
  pressed buttons, processed move / look vectors.
- HUD button-prompt hints (jump / attack / use / inventory) when looking at
  something, with the glyph set adapted to the detected controller type.
- Mixin Accessor for `CreativeInventoryScreen.selectedTab` / `setSelectedTab`
  replaces the previous reflection path.

### Fixed
- Creative double-tap A now toggles flight correctly. The previous build
  overwrote `input.playerInput.jump`, which masked the rising-edge detection
  Minecraft uses in `ClientPlayerEntity.tickMovement`.
- RB / LB now cycle creative inventory tabs reliably (Mixin Accessor instead
  of reflection plus chained `||` fallbacks).
- The OS mouse cursor is now centralised in `CursorCaptureManager`: hidden
  while the controller is driving any non-text-input mode, restored when the
  player switches to mouse, leaves the screen, or unplugs the controller.
- `TextFieldWidget` no longer traps the controller on screens without
  inventory slots (e.g. Create World). Any directional input releases focus
  and resumes widget navigation.
- Virtual keyboard navigation now uses lane-based scoring so each row
  (Lowercase / Backspace / ..., Cursor < / Cursor > / Cancel / Enter) is
  reachable consistently via left / right inside the row.

### Changed
- Single generic input layout. Glyphs still adapt to the detected controller
  type for display, but bindings are no longer duplicated per-type.
- `GameplayInputFrame` is mutable and reused every tick. The tick loop no
  longer allocates a frame per tick.
- `GuiNavigationController` split into focused helpers (`NavigationMode`
  resolver, `SlotNavigator`, `WidgetNavigator`, `CursorCaptureManager`,
  `CreativeTabNavigator`); all methods stay under 60 lines.
- `BetterControllerSettingsScreen` rewritten around a slider factory; the rest
  of the configuration moved to the JSON file.

### Removed
- Haptics / rumble architecture (`ControllerHaptics`, `HapticEvent`,
  `HapticProfile`) — GLFW provides no cross-platform rumble in this stack and
  the runtime was a permanent no-op.
- Radial menu (`RadialMenu`, `RadialMenuController`, `RadialMenuRenderer`,
  `RadialMenuSlot`).
- Animated inventory help panel.
- `ControllerPreset` and multi-layout overrides
  (xbox / playstation / switch / generic).

[0.3.0]: https://github.com/Nekuzaky/better-controller/releases/tag/v0.3.0+26.2
[0.1.0]: https://github.com/Nekuzaky/better-controller/releases/tag/v0.1.0
