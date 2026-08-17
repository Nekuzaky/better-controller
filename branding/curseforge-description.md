# CurseForge / Modrinth project copy

Paste-ready text for the project page. Keep it in sync with the README when features change.

---

## Summary (the one-line blurb, ~120 characters)

Play Minecraft Java with any gamepad. Analog look, full menu navigation, virtual keyboard — small and client-side.

---

## Description

**BetterController makes a gamepad feel native in Minecraft Java.**

Plug a controller in and play. No launcher wrapper, no key-mapping software, no
setup — the mod detects your pad, picks the right button glyphs, and gets out of
the way.

### What it does

- **Plug and play.** Xbox, PlayStation, Switch Pro and generic XInput pads are
  detected automatically, with matching on-screen glyphs. Hot-plugging works.
- **Analog everything.** Per-axis deadzone, anti-deadzone, response curve, and a
  camera sampled every rendered frame rather than every game tick — the stick is
  read at the same point in the frame as the mouse, so aiming stays responsive.
- **Every menu, not just the game.** Inventories, chests, the creative search,
  options, the world list. The right stick scrolls, the bumpers switch tabs, and
  a virtual keyboard covers chat and text fields.
- **Creative double-tap fly** works the way it does on keyboard, because the mod
  drives the real key bindings instead of faking jump pulses.
- **A settings screen** with the five sliders most people actually change, and a
  live-reloading JSON file for everything else.
- **Debug overlay** (`F8`) showing raw axes, triggers, processed vectors and the
  pressed buttons — handy when tuning or reporting a problem.

### What it deliberately does not do

No rumble: GLFW, the input backend Minecraft ships, exposes no cross-platform
rumble API, and shipping an architecture that silently does nothing helps nobody.
No radial menu: the vanilla hotbar and inventory are already usable with a pad.
The mod does one job and stays small.

### Two players on one machine

Minecraft Java has no splitscreen and a mod cannot add one. Two game instances
side by side on a LAN world do work, and each instance can be pinned to its own
controller with `preferredControllerGuid` / `preferredJoystickIndex`.

### Requirements

- Minecraft 1.21.11
- Fabric Loader 0.18.4 or newer
- Fabric API
- Client side only — nothing to install on the server

### Links

- Source, issues and full configuration reference: https://github.com/Nekuzaky/BetterController
- Licensed MIT.

---

## Notes for whoever publishes this

- **Project logo**: `branding/logo-512.png`. CurseForge accepts up to 400×400 and
  downscales; Modrinth wants 512×512. The same file covers both.
- **Gallery image**: `branding/banner-1280x720.png`. Add real in-game screenshots
  next to it — the F8 overlay and the controller-navigated creative inventory are
  the two that show what the mod does.
- **Categories**: Client-side, Utility & QoL, Miscellaneous.
- **Version name** on the file: `BetterController 0.1.0 for 1.21.11`.
- Tick **client-side only** in the project settings; it changes how modpack tools
  treat the mod.
