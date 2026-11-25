<!-- Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause -->
# Plugin Hub review notes

Ayra's Doggies is a cosmetic plugin with a fixed catalog of 110 dog appearances.
It uses the game's existing models and animation sequences. No custom model files,
asset URLs, arbitrary NPC IDs, gameplay guidance, or game actions are accepted.

## Runtime behavior

- Each replacement is a client-only RuneLiteObject. The original actor's
  composition, equipment, animations, hitpoints, position, and server state are
  untouched. Overhead UI is preserved.
- Remote replacements require an explicit appearance announcement from a current
  RuneLite Party member whose display name matches the announcement. Non-users
  stay human. Unknown IDs, versions, names, and worlds are rejected.
- Remote appearances are disabled in the Wilderness, on PvP/Deadman/Bounty/LMS/
  PvP Arena/tournament worlds, and in areas flagged for PvP. The target's location
  also prevents replacing a player across the Wilderness ditch from outside it.
- Hovering a remote player's native model reveals the original for native player
  interactions. No menu entries are created, removed, or reordered by this plugin;
  no input or server actions are synthesized. Hiding the scene model uses RuneLite's
  render callback; the replacement is removed when the native model is revealed.
- Death, native NPC transformations, invalid scene positions, despawns, scene
  loading, party changes, and shutdown clean up the replacements.

## Networking and privacy

**Share with party defaults to off.** Enabling it permits broadcasting only the
local in-game name, world number, allowlisted dog ID, protocol version, and enabled
state through the existing RuneLite Party connection. No coordinates, equipment,
chat, account credentials, or combat data are transmitted. With sharing off and
no earlier advertisement, no presence message is sent.

The plugin does not create, join, or rejoin a party. It has no custom HTTP client,
server, account integration, presence relay, or external asset service. Heartbeats
are sent every 20 game ticks; opt-out is sent immediately. Presence expires after
45 seconds. At most 256 member appearances are stored; repeated enabled messages
from one member are limited to one accepted update per 500 ms. Disabling messages
are accepted immediately. All mutable game/rendering state is accessed on the
client thread, with lifecycle checks on queued callbacks.

## Build and resources

- `build=standard`, Java 11 bytecode, and only the client-provided production API.
- BSD-2-Clause license; original 48 x 48 paw icon.
- Main plugin class and discovery properties match the display name.
- Test dependencies, reflection-based test injection, cache catalog tools, icon
  generator, and local installer are outside the production source set/JAR.
- No production reflection, native code, subprocesses, runtime filesystem access,
  custom Gson/HTTP clients, chat modification, input injection, or menu actions.
- Model metadata is a fixed catalog read from a local cache during development;
  it is not downloaded or modified at runtime.

Rules checked against the current official sources on 2026-11-25:

- [Submission requirements](https://github.com/runelite/plugin-hub/blob/master/README.md)
- [Review scope](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Rejected features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [Disallowed APIs](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/disallowed-apis.txt)
- [Standard build](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/standard-build.gradle)
- [Jagex guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)

The automated tests cover catalog completeness, model-specific animation choices,
message validation, opt-in defaults, sender/world matching, opt-out, Wilderness
boundaries, PvP exclusions, hover reveal, expiry, scene cleanup, and lifecycle.
Live two-client multiplayer and visual testing remain unverified. RuneLite's CI
and maintainers determine acceptance; this document does not claim approval.
