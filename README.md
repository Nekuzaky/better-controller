<h1 align="center">BetterController</h1>

<p align="center">
  <strong>Play Minecraft Java with any gamepad.</strong><br>
  Lightweight, zero-allocation Fabric mod with analog look, full GUI navigation, creative double-tap fly and a virtual keyboard.
</p>

<p align="center">
  <a href="https://github.com/Nekuzaky/better-controller/actions/workflows/ci.yml">
    <img alt="CI" src="https://img.shields.io/github/actions/workflow/status/Nekuzaky/better-controller/ci.yml?branch=mc%2F1.21.11&label=CI&logo=github">
  </a>
  <a href="https://github.com/Nekuzaky/better-controller/releases/latest">
    <img alt="Release" src="https://img.shields.io/github/v/release/Nekuzaky/better-controller?include_prereleases&sort=semver&label=release&color=blue">
  </a>
  <a href="https://github.com/Nekuzaky/better-controller/releases">
    <img alt="Downloads" src="https://img.shields.io/github/downloads/Nekuzaky/better-controller/total?color=brightgreen">
  </a>
  <a href="https://www.curseforge.com/minecraft/mc-mods/bettercontroller">
    <img alt="CurseForge" src="https://img.shields.io/badge/curseforge-download-F16436?logo=curseforge&logoColor=white">
  </a>
  <img alt="Minecraft" src="https://img.shields.io/badge/minecraft-1.21.11-62b132?logo=minecraft&logoColor=white">
  <img alt="Fabric" src="https://img.shields.io/badge/fabric-0.19.5-blueviolet">
  <img alt="Java" src="https://img.shields.io/badge/java-21-orange?logo=openjdk&logoColor=white">
  <a href="LICENSE"><img alt="License" src="https://img.shields.io/github/license/Nekuzaky/better-controller?color=blue"></a>
</p>

<p align="center">
  <a href="https://github.com/Nekuzaky/better-controller/issues">
    <img alt="Issues" src="https://img.shields.io/github/issues/Nekuzaky/better-controller?color=informational">
  </a>
  <a href="https://github.com/Nekuzaky/better-controller/pulls">
    <img alt="Pull requests" src="https://img.shields.io/github/issues-pr/Nekuzaky/better-controller?color=informational">
  </a>
  <a href="https://github.com/Nekuzaky/better-controller/stargazers">
    <img alt="Stars" src="https://img.shields.io/github/stars/Nekuzaky/better-controller?style=social">
  </a>
</p>

<p align="center">
  <a href="https://buymeacoffee.com/Nekuzaky">
    <img alt="Buy Me a Coffee" src="https://img.shields.io/badge/buy%20me%20a%20coffee-support-FFDD00?logo=buymeacoffee&logoColor=black">
  </a>
  <a href="https://www.patreon.com/Nekuzaky">
    <img alt="Patreon" src="https://img.shields.io/badge/patreon-support-FF424D?logo=patreon&logoColor=white">
  </a>
  <a href="https://github.com/sponsors/Nekuzaky">
    <img alt="GitHub Sponsors" src="https://img.shields.io/badge/sponsors-support-EA4AAA?logo=githubsponsors&logoColor=white">
  </a>
</p>

---

## Why

Minecraft has no native gamepad support on Java. Existing solutions ship 7k+
lines of code and load 4 controller layouts even if you only own one stick.
BetterController does one job — **make a gamepad feel native** — and tries to
stay small, predictable and allocation-free while doing it.

## Features

- **Plug-and-play.** Auto-detects Xbox, PlayStation, Switch Pro and generic
  XInput controllers, swaps glyphs accordingly, survives hot-disconnect.
- **Analog everything.** Radial deadzone on movement, so a diagonal escapes it at
  the same distance as a cardinal and the stick direction is never bent. Per-axis
  deadzone, anti-deadzone, response curve and adaptive smoothing on the look
  stick.
- **Full GUI navigation.** Inventories, chests, creative search, options,
  world list, virtual keyboard for chat. Right stick scrolls scrollable
  screens (creative inventory, world list).
- **Creative double-tap fly.** The original toggle works — no synthetic
  jump pulses fighting Minecraft's native detection.
- **5-slider settings screen.** Movement deadzone, look X / Y sensitivity,
  look speed multiplier, trigger threshold. Everything else in JSON.
- **Debug overlay** (`F8`) showing raw axes, triggers, processed vectors,
  pressed buttons.
- **No vibration noise, no radial menu bloat.** Removed on purpose — see
  [Why no rumble?](#why-no-rumble) below.

## Install

1. [Fabric Loader](https://fabricmc.net/use/) for Minecraft 1.21.11.
2. [Fabric API](https://modrinth.com/mod/fabric-api) in `mods/`.
3. `bettercontroller-<version>+1.21.11.jar` in `mods/`, from either
   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bettercontroller)
   or the [GitHub releases](https://github.com/Nekuzaky/better-controller/releases).
4. Launch. Plug a controller. Done.

Using a launcher with a mod manager (CurseForge app, Prism, MultiMC)? Install
from CurseForge and it handles the Fabric API dependency for you.

## Default bindings

| Action                   | Button         |
|--------------------------|----------------|
| Movement                 | Left stick     |
| Look                     | Right stick    |
| Menu scroll              | Right stick Y  |
| Jump                     | A / Cross      |
| Sprint                   | L3             |
| Sneak                    | R3             |
| Attack / mine            | RT             |
| Use / place              | LT             |
| Inventory                | Y / Triangle   |
| Drop item                | B / Circle     |
| Swap hands               | X / Square     |
| Pick block / Hotbar next | RB             |
| Hotbar previous          | LB             |
| Open chat                | D-pad ↑        |
| Toggle perspective       | D-pad ↓        |
| Pause                    | Start          |
| Player list              | Back / Select  |

Menus: D-pad / left stick navigate, A confirms, B goes back, RB/LB switch
tabs, RT/LT page, right stick scrolls.

## Configure

The runtime config is at `config/bettercontroller.json` and reloads live
within ~500 ms.

The in-game settings screen
(`Options → Controls → Controller Settings`, or the button on the pause
menu) has the five sliders most users want:

- Movement deadzone
- Look sensitivity X
- Look sensitivity Y
- Look speed multiplier
- Trigger threshold

Everything else — response curve, smoothing, bindings, axes — lives in the
JSON. See
[`src/main/resources/bettercontroller.default.json`](src/main/resources/bettercontroller.default.json)
for the full schema with comments.

### Rebinding

```jsonc
{
  "bindings": {
    "jump": ["A"],
    "attack": ["RT"],
    "menu_confirm": ["A"]
  },
  "axes": {
    "move_x": "LEFT_X",
    "move_y": "LEFT_Y",
    "look_x": "RIGHT_X",
    "look_y": "RIGHT_Y"
  }
}
```

- Aliases supported: `A`/`CROSS`/`SWITCH_A`/`SOUTH`, `LB`/`L1`, `RT`/`R2`, ...
- Prefix an axis token with `-` to invert it (e.g. `"-RIGHT_Y"`).
- An action can have multiple bindings: `"hotbar_next": ["RB", "RT"]`.

### Two players on one machine

Minecraft Java has no splitscreen, and a mod cannot add one — the client is built
around a single player, world, camera and GUI. What does work is **two game
instances side by side**, both joined to the same LAN world (two Microsoft
accounts required; Prism Launcher or MultiMC handle parallel instances well).

For that, each instance has to claim its own pad — otherwise both grab the first
gamepad and drive the same character. Pin them in each instance's config:

```jsonc
{
  "preferredControllerGuid": "78696e70757401000000000000000000",
  "preferredJoystickIndex": -1
}
```

- `F8` shows the **slot** and **GUID** of the pad currently in use.
- The GUID identifies a device *model*, so two identical pads share one. To tell
  those apart, leave the GUID empty and set `preferredJoystickIndex` (`0`, `1`, …).
- Set both and the index must also match the GUID — useful when slots move around.
- Leave them at `""` / `-1` for the default behaviour: first gamepad found.
- If a preference is set and nothing matches, **no** controller is used, on
  purpose: a second instance must never steal the first one's pad. The reason is
  logged once.

### Quick tuning

| Goal                       | Try                                                       |
|----------------------------|-----------------------------------------------------------|
| Snappier camera            | `lookSpeedMultiplier` 2.5–3.0                             |
| Less stick drift           | `movementDeadzone` 0.14–0.18                              |
| Softer center, fast outer  | `lookResponseCurve` `exponential_light`                   |
| Faster menu navigation     | `menuInitialRepeatDelayMs` 90, `menuRepeatIntervalMs` 30  |
| Slower triggers feel laggy | Lower `triggerThreshold` to 0.35                          |

## Architecture

Two clocks. Buttons, movement and menus are tick-paced (20 Hz, `ClientTickEvents`).
The camera is frame-paced: a Mixin at the head of `GameRenderer.render` samples the
right stick every rendered frame, at the same point in the frame where vanilla
applies the mouse — right after `Mouse.tick()` and before the frame's camera is
built.

```
ControllerPoller
     │  ControllerSnapshot
     ▼
InputTranslator ───► GameplayInputFrame  (mutable, reused per tick)
     │
     ├──► updateLook()               per frame: camera delta only
     │
     ├──► MinecraftInputApplier      keybindings + analog vector
     │
     └──► ControllerGuiNavigationHooks
              │
              ▼
          GuiNavigationController
              ├── SlotNavigator         (handled screens)
              ├── WidgetNavigator       (clickable widgets)
              ├── CreativeTabNavigator  (creative inventory tabs)
              └── CursorCaptureManager  (OS cursor)
```

### Design constraints (NASA Power of Ten, adapted to Java)

- **All functions stay under 60 lines.**
- **The camera never depends on the HUD.** Look runs on the world render pass,
  so hiding the HUD (`F1`) does not freeze it.
- **Zero allocation in the steady-state tick loop.** `GameplayInputFrame` is
  a single mutable instance, `input.movementVector` is mutated in place, and
  `input.playerInput` is left to `KeyboardInput.tick` so Minecraft's native
  rising-edge detection (used by creative double-tap fly) keeps working.
- **No reflection.** `setSelectedTab` on the creative inventory is reached
  through a Mixin Accessor / Invoker.
- **All loops bounded.** Fixed-size snapshots, finite widget / slot lists.
- **Validate at boundaries**, not in hot paths.

## Build from source

```bash
# Windows
.\gradlew.bat build
.\gradlew.bat runClient

# Linux / macOS
./gradlew build
./gradlew runClient
```

Artifacts land in `build/libs/bettercontroller-<version>+1.21.11.jar`.

Gradle runs on JDK 25 (Fabric Loom 1.18 requires it); the mod itself is
compiled for Java 21, so players keep the Java Minecraft 1.21.11 ships with.
Set `JAVA_HOME` to a JDK 25 before running the wrapper.

## Branching model

One branch per Minecraft version. Each is complete on its own: it builds,
tests, releases and gets patched independently.

| Branch       | Minecraft | Loader | Players need | Build JDK |
|--------------|-----------|--------|--------------|-----------|
| `main`       | 26.2      | Fabric | Java 25      | 25        |
| `mc/1.21.11` | 1.21.11   | Fabric | Java 21      | 25        |
| `mc/1.20.1`  | 1.20.1    | Fabric | Java 17      | 25        |
| `mc/1.12.2`  | 1.12.2    | Forge  | Java 8       | 8         |

`main` always tracks the newest supported Minecraft. When a new version is
ported, the previous one moves to its own `mc/<version>` branch.

```bash
git checkout mc/1.21.11
./gradlew build
```

Releases are tagged `v<mod>+<minecraft>` (e.g. `v0.3.0+1.21.11`) and the JAR
is named the same way, so every line can ship the same mod version without
collisions. The release workflow refuses a tag whose version or Minecraft
version doesn't match the branch it was pushed from.

## FAQ

### Why no rumble?

GLFW (the input backend Minecraft ships) doesn't expose a cross-platform
rumble API. The old codebase carried a full haptics architecture that
silently no-op'd. We deleted it. If LWJGL adds rumble support in a future
release, it'll come back as a clean addition rather than dead architecture.

### Why no radial menu?

It was a custom feature competing with the vanilla hotbar and inventory.
Both are already perfectly usable with a controller via this mod, so the
radial menu added complexity without enough value. Removed.

### Why single layout?

The previous version duplicated bindings four times (Xbox / PlayStation /
Switch / generic) to display friendly button names. Now the *bindings* are
unified (generic token names) and the *glyphs* still adapt to your
controller. Same UX, ~400 fewer lines of config.

### My favourite controller isn't detected.

Two different problems hide behind that sentence — press `F8` to tell them apart.

**The overlay names your pad but the glyphs are wrong.** The pad works; only the
type heuristic missed. Open an
[issue](https://github.com/Nekuzaky/better-controller/issues/new?template=bug_report.yml)
with the name and GUID shown by `F8`; detection is heuristic and easy to extend.

**The overlay says there is no usable gamepad.** GLFW only reports a joystick as
a *gamepad* when it has an SDL mapping for that device, and without one the pad is
invisible rather than mis-detected. GLFW ships a mapping database, but it is a
snapshot from when that GLFW version was built, so recent pads and
DirectInput-only devices fall through.

Fix it without waiting for a GLFW update: create
`config/bettercontroller-mappings.txt` and paste in the line for your device from
[SDL_GameControllerDB](https://github.com/mdqinc/SDL_GameControllerDB) (the whole
file works too). The mod applies it at startup and logs how many mappings it took.
`F8` prints the GUID you need to match, and the log names the device.

The database is not bundled on purpose: it is around a megabyte of text, which
would dwarf the mod itself, for a case most players never hit.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). The TL;DR:

- Branch from the version branch you're fixing (`main` for the newest
  Minecraft) and target it in your PR.
- One concern per PR.
- Test with a real controller and describe what you tested.

Read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md) and
[SECURITY.md](SECURITY.md) for the rest.

## Support

BetterController is free and MIT-licensed, and it stays that way. If it made
your gamepad usable and you want to help cover the time spent chasing
Minecraft version bumps and controller quirks:

- [**Buy Me a Coffee**](https://buymeacoffee.com/Nekuzaky) — one-off, no account needed.
- [**Patreon**](https://www.patreon.com/Nekuzaky) — monthly, for build previews and version-bump notes.
- [**GitHub Sponsors**](https://github.com/sponsors/Nekuzaky) — same thing, billed through GitHub.

Nothing in the mod is paywalled and nothing ever will be. Starring the repo
and filing good bug reports helps just as much.

## License

[MIT](LICENSE) — do what you want, just keep the copyright notice.
