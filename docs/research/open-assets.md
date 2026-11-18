# Open-source resources for the island and the aquarium

What free, open resources could raise the island's and the aquarium's look, and how they would
fit the game's own renderer (meshes in code, colour per vertex, no textures; a Fairphone 4 is the
reference phone). Nothing here is used yet: the downloads need the developer's go-ahead.

## 1. 3D models under CC0 (public domain, no credit required)

| Pack | What's in it | Formats | Fit |
| --- | --- | --- | --- |
| [Quaternius: Stylized Nature MegaKit](https://quaternius.com/packs/stylizednaturemegakit.html) ([itch.io](https://quaternius.itch.io/stylized-nature-megakit), [OpenGameArt](https://opengameart.org/content/stylized-nature-megakit)) | 40 trees, 35 plants and flowers, 27 rocks, grass, bushes | FBX, OBJ, glTF; Standard zip about 100 MB, free | The island's trees, bushes, flowers and rocks; the aquarium's plants and stones |
| [Kenney: Pirate Kit](https://opengameart.org/content/pirate-kit) ([announcement](https://gamedev.net/news/1696-pirate-kit/)) | 60 to 70 pirate props: barrels, crates, flags, docks, a ship | OBJ, FBX, glTF | The market's and the pier's props, the wreck's details |
| [Kenney: Watercraft Kit](https://www.kenney.nl/assets/watercraft-kit) ([itch.io](https://kenney-assets.itch.io/watercraft-kit)) | About 45 boats and ships | OBJ, FBX, glTF | Small boats moored around the island (never the player's boat, which is the skins' own design) |
| [Kenney: Nature Kit](https://opengameart.org/content/nature-kit) | Over 330 nature pieces: trees, rocks, plants, terrain | OBJ, FBX | Alternative to Quaternius for rocks and plants |
| [Quaternius: animated fish](https://quaternius.itch.io/lowpoly-animated-fish) ([OpenGameArt](https://opengameart.org/content/animated-fish)) | Low poly fish, sharks, a dolphin, a whale, a manta | Blend, OBJ, FBX; about 1 MB | Not for the species (their looks must match the Dex), but a passing manta or turtle outside the aquarium's glass, or around the island |
| [KayKit packs (Kay Lousberg)](https://kaylousberg.itch.io/resource-bits) ([OpenGameArt](https://opengameart.org/content/kaykit-dungeon-pack-10)) | Many themed low poly kits (resources, furniture, city builder) | glTF, FBX, OBJ | Furniture for the aquarium's room; crates, sacks and tools for the rope workshop |

All are CC0 ([Quaternius](https://opengameart.org/content/all-cc0-uploader-quaternius) and
Kenney say so on every pack): usable in a sold game without credit. The game would still credit
them, like its sounds (`assets/audio/CREDITS.md`).

### How they would fit the renderer

- **A converter at build time** (`tools/assets/`): OBJ with its MTL colours (or the pack's
  colour-map texture, sampled once per vertex) into a small binary of positions, normals and
  vertex colours in `assets/models/`, loaded as `MeshData` and drawn with the painted material.
  The island's baked light (sun shadows and ambient occlusion in the colours) applies to them
  like to the hand-made parts, so nothing looks pasted on.
- **A budget**: only the pieces used are kept (a few dozen), the village's triangle budget
  (`VillageSceneTest`) still holds, and the APK grows by a few hundred KB at most.
- **Credits and checks**: one line per model (pack, author, licence, source page) in
  `assets/models/CREDITS.md` and Settings > Credits, checked by a test like `AudioAssetsTest`.
- **What stays hand-made**: the six buildings (the game's identity, and the skins tint their
  parts), the player's boat and every fish species.

## 2. Techniques from open projects

- **Fish living together**: Craig Reynolds' boids (separation, alignment, cohesion) with the
  predator avoidance that games add for fish: prey flee a predator first, then the school
  regroups ([UAF lecture](https://www.cs.uaf.edu/2015/spring/cs482/lecture/01_26_boids.html),
  [schooling simulator](https://homo-deus-lab.pages.dev/lab/marine-biology/fish-schooling/),
  [boids chapter](https://kamran-afzali.github.io/Agent-Based-Modelling/the-boids-flocking-model.html)).
  **Done**: `AquariumSchool` (schools, chases, meetings, nibbling, following a big fish).
- **Caustics and light shafts under water**: stylised caustics mapped in world space and
  half-resolution god rays are the usual mobile recipe
  ([Godot: water with caustics](https://godotshaders.com/shader/water-with-caustics/),
  [Synty drop-in water shader, MIT](https://godotshaders.com/shader/synty-core-drop-in-water-shader/),
  [Defold stylised water](https://forum.defold.com/t/stylized-water-shader/82202)). Proposed:
  moving caustics on the aquarium's sand and decor (one noise lookup, like the village's sea),
  softer light shafts.
- **Trees that sway**: the CC0 trees' leaves marked as a fluttering part, moved in the vertex
  shader like the village's flags (`deform.glsl`), at no CPU cost.
