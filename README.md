<h1 align="center">BetterController</h1>

<p align="center">
  <strong>Play Minecraft Java with any gamepad.</strong><br>
  Lightweight, zero-allocation Forge mod with analog look, full GUI navigation, creative double-tap fly and a virtual keyboard.
</p>

<p align="center">
  <a href="https://github.com/Nekuzaky/better-controller/actions/workflows/ci.yml">
    <img alt="CI" src="https://img.shields.io/github/actions/workflow/status/Nekuzaky/better-controller/ci.yml?branch=mc%2F1.12.2&label=CI&logo=github">
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
  <img alt="Minecraft" src="https://img.shields.io/badge/minecraft-1.12.2-62b132?logo=minecraft&logoColor=white">
  <img alt="Forge" src="https://img.shields.io/badge/forge-14.23.5.2847-blueviolet">
  <img alt="Java" src="https://img.shields.io/badge/java-8-orange?logo=openjdk&logoColor=white">
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
  controllers, swaps glyphs accordingly, survives hot-disconnect.
- **Analog everything.** Per-axis circular deadzone, anti-deadzone, response
  curve, camera sampled every rendered frame.
- **GUI navigation.** Buttons, sliders, inventories and containers (RB
  shift-moves a stack, RT splits one), world / server / resource-pack /
  language lists, with right-stick scrolling.
- **Virtual keyboard** on any text field: chat, signs, anvils, world and
  server names.
- **HUD prompts and connection banners**, with glyphs for the detected pad.
- **Debug overlay** (`F8`, rebindable) showing raw axes, triggers, processed
  vectors, pressed buttons and the pad's GUID.
- **Two instances, two pads.** Pin each game instance to one controller.

### Compared to the Fabric versions

1.12.2 is a Forge port of the same core. Not there yet: the in-game settings
screen (everything is in the JSON), creative-inventory tab switching, and
hiding the OS cursor while the pad drives a menu. Video Settings rows and the
Controls key list are reachable only through their normal buttons.

## Install

1. [Minecraft Forge](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html)
   14.23.5.2847 or newer for Minecraft 1.12.2.
2. `bettercontroller-<version>+1.12.2.jar` in `mods/`, from either
   [CurseForge](https://www.curseforge.com/minecraft/mc-mods/bettercontroller)
   or the [GitHub releases](https://github.com/Nekuzaky/better-controller/releases).
3. Launch. Plug a controller. Done.

1.12.2 predates LWJGL 3, so the mod ships its own GLFW and loads it in an
isolated classloader next to the game's LWJGL 2. Nothing else to install.

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
within ~500 ms. There is no settings screen on 1.12.2 yet: response curve,
sensitivity, deadzones, bindings and axes all live in the JSON. See
[`src/main/resources/bettercontroller.default.json`](src/main/resources/bettercontroller.default.json)
for the full schema.

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

### Quick tuning

| Goal                       | Try                                                       |
|----------------------------|-----------------------------------------------------------|
| Snappier camera            | `lookSpeedMultiplier` 2.5–3.0                             |
| Less stick drift           | `movementDeadzone` 0.14–0.18                              |
| Softer center, fast outer  | `lookResponseCurve` `exponential_light`                   |
| Faster menu navigation     | `menuInitialRepeatDelayMs` 200, `menuRepeatIntervalMs` 100 |
| Slower triggers feel laggy | Lower `triggerThreshold` to 0.35                          |

## Architecture

```
GlfwBridge  (LWJGL 3 GLFW in its own classloader, next to the game's LWJGL 2)
     │
ControllerPoller ──► ControllerSnapshot
     │
InputTranslator ───► GameplayInputFrame  (mutable, reused per tick)
     │
     ├──► MinecraftInputApplier + ScreenExitGate   key bindings, analog movement
     │
     └──► BetterControllerEventHandler  (Forge client events)
              ├── GuiScreenNavigator     buttons, sliders, text fields, lists
              ├── ContainerNavigator     inventory / container slots
              └── VirtualKeyboardOverlay
```

### Design constraints (NASA Power of Ten, adapted to Java)

- **All functions stay under 60 lines.**
- **Zero allocation in the steady-state tick loop.**
- **No mixins.** Everything hangs off Forge events. Vanilla's protected GUI
  members are reached through `GuiReflection`, which looks each one up by both
  its MCP and SRG name and degrades with a single warning — a development-name
  lookup alone works in `runClient` and silently fails in an installed game.
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

Artifacts land in `build/libs/bettercontroller-<version>+1.12.2.jar`.

Requires JDK 8. ForgeGradle 2.3 — the only toolchain that still builds
1.12.2 — pins Gradle 4.10.3 and Java 8, which is also what the game runs on.
Set `JAVA_HOME` to a JDK 8 before running the wrapper.

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

Open an [issue](https://github.com/Nekuzaky/better-controller/issues/new?template=bug_report.yml)
with the GLFW name and GUID — `F8` debug overlay shows both. Detection is
heuristic and easy to extend.

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
