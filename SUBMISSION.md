<!-- Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause -->
# Plugin Hub review notes: Ayra's Prop Hunt

This submission has expanded from Doggies into a searchable cosmetic model picker.
The display name is **Ayra's Prop Hunt**. It does not implement competitive hide-and-seek
rounds, NPC attack prediction, targeting assistance, or gameplay automation.

## Model selection

The bundled metadata snapshot contains 168,077 entries: 17,000 NPC definitions,
67,836 scenery definitions/shape variants, 20,167 item models, and all 63,074 mesh
IDs in the current game model index. Users search a named list and choose a
catalog entry. Selection, persisted configuration, and incoming party messages
are resolved only against that catalog; nonexistent keys are never loaded.
No arbitrary animation IDs, model files, paths, URLs, or remote assets are accepted.

The catalog includes a raw-mesh category. This broader scope may require specific
maintainer review under the Hub's restrictions on ID-based plugins and significant
cosmetic asset changes. This document does not assert that a successful CI build
constitutes acceptance of that scope.

The resource contains only names and numeric assembly/animation metadata. It has
no game vertices, textures, sounds, or other artwork. Game meshes are obtained
through Client.loadModelData. The metadata generator is an offline developer tool
using RuneLite's own cache decoder and opens the game cache read-only.

## Rendering and animation

Replacements are RuneLiteObjects, with a shared bounded cache of at most 32 base
models and independent animation controllers per player. Parts, recolors,
retextures, native scale, offsets, full-color overrides, and mirrored scenery
are applied to private/merged buffers. Actors' positions, equipment, compositions,
health, action animations, collision, and server state remain unchanged.

NPC idle/walk/run sequences come from their definitions. Known compatible attack
sequences are matched from named animation data and mirror the local player's
interaction action. They are cosmetic, use no future game information, and send
no combat information to anyone else. Unknown rigs remain static or retain their
own idle animation. Native death and NPC transformations remain visible.

Render callbacks preserve overhead UI. Hovering a remote player's original
model reveals it for native player interactions. No menu entries, game actions,
input injection, or script invocation are used. Despawns, loading, invalid scene
positions, party changes, logout, and shutdown clean up replacements.

Remote props are disabled in the Wilderness, flagged PvP areas, and
PvP/Deadman/Bounty/LMS/Arena/tournament worlds. A target-location check also
protects players across the Wilderness ditch when viewed from outside.

## Sharing and UI

Share with party defaults to off. An opted-in player shares only their current
in-game name, world, catalog key, protocol version, and enabled state through
RuneLite Party. No coordinates, gear, chat, credentials, or combat data are sent.
The plugin never joins/rejoins/changes parties and has no custom HTTP client or
external relay. Senders must be current party members and may announce only the
name supplied by their party identity. Worlds, versions, names, and keys are
validated. Incoming enabled updates are limited to one accepted update per member
per 500 ms; opt-outs are immediate. Presence is capped at 256 members and expires
after 45 seconds. Heartbeats are sent every 20 game ticks.

The search sidebar uses public ClientToolbar/PluginPanel APIs. Search is debounced,
cancellable, and performed on SwingWorker threads. At most 250 matches are displayed
at once, while the complete match count is shown. Keyboard, mouse, Enter/double-click,
category filters, and a return-to-normal button are supported. The plugin does not
inspect or modify another plugin's configuration or UI.

## Build and verification

- Standard build; Java 11 bytecode; BSD-2-Clause license.
- Original 48 x 48 box icon and an offline screenshot of the actual Swing panel.
- Only client-provided production dependencies. Catalog-generator dependencies
  exist in a separate offline tool project and are absent from the plugin JAR.
- No production reflection, native code, subprocesses, runtime disk reads/writes,
  custom Gson/HTTP clients, credentials access, or synthetic input.
- Tests cover catalog/search completeness, Zuk/bench/chicken, keyboard selection,
  static props, attack/locomotion sequences, message validation, opt-in/out,
  Wilderness/PvP restrictions, hover reveal, expiry, loading, and cleanup.
- Live in-game visuals and two-client multiplayer have not been verified.

Current source references:

- [Submission rules](https://github.com/runelite/plugin-hub/blob/master/README.md)
- [Rejected/rolled-back features](https://github.com/runelite/runelite/wiki/Rejected-or-Rolled-Back-Features)
- [Review scope](https://github.com/runelite/runelite/wiki/Plugin-Hub-Review)
- [Disallowed APIs](https://github.com/runelite/plugin-hub-tooling/blob/master/package/src/main/resources/net/runelite/pluginhub/packager/disallowed-apis.txt)
- [Jagex guidelines](https://secure.runescape.com/m=news/third-party-client-guidelines?oldschool=1)
