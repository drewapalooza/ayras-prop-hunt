<!-- Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause -->
# Ayra's Doggies

Just a fun little mod for people who got that dog in them.

Choose a dog, become a dog, and see your opted-in RuneLite Party friends as dogs.

## Choose your dog

Enable **Ayra's Doggies**, open its settings cog, and use **Your dog**. The choice updates
immediately and is saved in your RuneLite profile.

The picker contains **110 appearances**, verified from the current game cache:

- All **12 breeds from A Ruff Situation**, each with three adult coats and three
  puppy coats: **72 choices**. These include Labrador, Chihuahua, Border Collie,
  Corgi, Greyhound, Husky, Pug, Samoyed, Bernese Mountain Dog, Shiba, Spaniel, and Yorkie.
- Stray dogs (including the clean quest variant), classic dogs, guard dogs,
  bloodhound, sitting/sleeping sheepdogs, Varlamore breeds, named dogs, seasonal
  dogs, and supernatural hounds.

Each appearance uses its own verified idle/walk animations, coloring, lighting,
model parts, and native scale. Sitting and sleeping dogs stay seated/asleep while
moving; those models have no walking sequence. Quest/POH copies with identical
appearances are represented by a single picker entry where practical.

## See your friends as dogs

1. Everyone installs/enables this version of **Ayra's Doggies**.
2. Enable RuneLite's built-in **Party** plugin and join the **same party passphrase**.
3. Turn on **Share with party** and leave **Show party dogs** enabled in Ayra's Doggies settings.
4. Meet on the same game world, outside the Wilderness.

You see each opted-in party member as their selected dog. Other party members
without Ayra's Doggies remain human. Appearance changes are sent immediately; initial
connections or reconnects can take up to a heartbeat (about 12 seconds).

Sharing currently works through **RuneLite Party**. It does not automatically
discover every Ayra's Doggies user nearby. That broader discovery would require a
separately hosted presence relay; none is deployed or required for this version.
The plugin never creates a party or changes your existing party.

Other players return to normal as soon as you enter the Wilderness, a PvP area, or a PvP/Deadman world. Players on
the Wilderness side of the ditch also remain human when viewed from outside.
Your own local dog remains enabled, including in the Wilderness. Other players'
native overheads, names, health bars, and prayer icons remain intact.

**Hover another player to reveal their original model and use the game's normal
player interaction menu.** Move the cursor away to see their dog again. This
preserves native trade/follow interactions without synthesizing game actions.

Disabling sharing broadcasts an opt-out. Disabling Ayra's Doggies, leaving a party,
logout, a world change, despawning, or expired presence removes the replacement.
A client that disconnects without sending an opt-out expires after 45 seconds.

Only your name, current world, chosen dog ID, and enabled state are sent to your
current RuneLite Party. The plugin does not send coordinates, gear, stats, or
combat data. It does not change other players on the game server: clients without
Ayra's Doggies continue to see normal characters.

## Local behavior

Your equipment, movement, skills, combat, and actual action animations continue
normally. During actions the dog keeps its own idle/walk animation. Native NPC
transformations (such as quest cutscenes) and death show the game's normal model.
No game art is bundled; dog models load from the game's cache.

Plugin Hub submission is pending review.

## Build and install

Requires JDK 11 or newer (tested with JDK 17).

```powershell
git clone https://github.com/drewapalooza/ayras-doggies.git
cd ayras-doggies
.\gradlew.bat test release
```

Produces `ayras-doggies-1.2.0.jar` here and in `build/libs/`. Place it in
`%USERPROFILE%\.runelite\sideloaded-plugins`, keep only one active Ayra's Doggies JAR,
and restart your developer client. Stock RuneLite's normal launcher does not
load sideloaded JARs.

Or close the dev client and run `.\tools\Install-DogMode.ps1`. This verifies the
new JAR, disables older Ayra's Doggies versions, and installs the update. If the running
client still has an old JAR locked, the installer leaves the update staged.

For the source launcher, temporarily remove/disable the installed Ayra's Doggies JAR
so it cannot load alongside the built-in source version, then run:

```powershell
.\gradlew.bat run
```

To build against another RuneLite release, add `-PruneliteVersion=<version>`.

## Verification and catalog maintenance

Automated checks cover all 72 quest variants, per-model animations, malformed
network messages, chosen-dog updates, per-member opt-in, name matching, world
matching, Wilderness boundaries, hover interactions, stale presence, party
changes, loading, and cleanup. Live multiplayer and visual verification are
still needed.

The model/animation metadata was inspected read-only from the local Jagex cache.
To regenerate it, first save `javap -constants net.runelite.api.gameval.NpcID`
output to `build/dog-catalog/npc-ids.txt`, then run from this folder:

```powershell
node tools/catalog-dogs.cjs <cache-directory> <Gradle-lib/ant-1.10.14.jar>
node tools/generate-dog-models.cjs
```

The tools use the cache layout documented by RuneLite's
[cache module](https://github.com/runelite/runelite/tree/master/cache/src/main/java/net/runelite/cache).
Rendering uses [RuneLiteObject](https://github.com/runelite/runelite/blob/master/runelite-api/src/main/java/net/runelite/api/RuneLiteObject.java)
and [render callbacks](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/callback/RenderCallback.java).
Appearance sharing uses RuneLite's [PartyService](https://github.com/runelite/runelite/blob/master/runelite-client/src/main/java/net/runelite/client/party/PartyService.java).
