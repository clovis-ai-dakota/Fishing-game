# The village's look: research and decisions

How popular stylised mobile games get a rich, readable 3D map on a phone's budget, and what the
village (`ui/village/`) does as a result. The village is drawn by the game's own renderer
(meshes built in code, no textures), so every technique below had to fit that: no lightmap
textures and no post-processing pass on a mid-range GPU (a Fairphone 4 is the reference).

## 1. What the research says

### Baked light is the base of the mobile look

- Android's guide to mobile lighting calls baked lighting the first choice on phones and the
  cheapest: all the lighting is computed ahead of time, so it costs nothing per frame.
  ([Android Developers: Lighting for mobile games](https://developer.android.com/games/optimize/lighting-for-mobile-games-with-unity))
- Low-poly and stylised mobile games bake the light into **vertex colours** when the scene
  doesn't move: ambient occlusion and colour variation without any texture.
  ([Polycount: performance through vertex colour](https://polycount.com/discussion/157219/increasing-mobile-game-performance-through-vertex-color-article),
  [Tripo: vertex colour workflows](https://www.tripo3d.ai/blog/explore/smart-mesh-vertex-color-workflows-for-low-poly-meshes))
  The catch: vertex lighting depends on how finely the mesh is cut, so it needs a fine ground
  grid. ([arXiv: procedural lightmaps](https://arxiv.org/pdf/1808.00328))
- Real-time soft shadows are expensive on the tile-based GPUs of phones, and a small 2x2 filter
  isn't enough to hide the steps, so mobile games either bake them or render a shadow map once
  for the still scene. ([Unity: shadow optimisation](https://docs.unity3d.com/Manual/shadows-optimization.html),
  [Arm, SIGGRAPH 2016: mobile shadows](https://community.arm.com:443/cfs-file/__key/communityserver-blogs-components-weblogfiles/00-00-00-20-66/3_2D00_mmg_2D00_siggraph2016_2D00_shadows_2D00_marius.pdf))
- Ambient occlusion is what grounds objects; the developer of a cozy city builder notes how much
  of a difference it makes. ([80.lv](https://80.lv/articles/screen-space-ambient-occlusion-test-for-upcoming-cozy-city-builder))
  *Survival Kids* (Unity) grounds its stylised world with AO, coloured soft shadows tinted in
  three colours, and keeps post-processing to tonemapping, bloom and colour correction.
  ([Unity blog: Survival Kids](https://unity.com/vn/blog/graphics-and-rendering-tips-from-survival-kids))

### Light, colour and readability

- *Rise of Kingdoms* sets the sun at about 45 degrees so shadows match the buildings' height,
  slightly behind the objects for volume; light is warm and shadows cool, matched across props.
  ([Room 8 Studio: game art analysis, atmosphere](https://room8studio.com/news/game-art-analysis-pt-2-atmosphere/))
- The same analysis: saturated colours are kept for what can be used, contrast and detail
  concentrate on the focal points, and a brightness gradient (darker at the bottom, lighter at the
  top) gives depth and keeps small objects legible on large maps.
- Stylised environments rely on strong silhouettes and shape language rather than surface
  detail, and knowing when to stop: if shape and focal points work, extra polish adds nothing.
  ([Mobidictum: Age of Magic](https://mobidictum.com/?p=410027), [80.lv: environment design](https://80.lv/articles/the-strings-of-environment-design/))
  A check: the scene must still read in greyscale.
- Bevelled edges catch the light where hard-edged boxes look like cardboard; the technique has
  no single best practice but is everywhere in stylised art.
  ([Radiator blog: bevels in video games](https://www.blog.radiator.debacle.us/2017/07/bevels-in-video-games.html))
- Croakwood darkens everything outside the playable area, like a fog measured from the play
  area rather than the camera: it frames the scene and hides its edges.
  ([Croakwood devlog 7](https://www.texelraptor.com/blog/croakwood-devlog-7-rendering))
- Tilt-shift (a blurred top and bottom) makes a map read as a miniature, but it's a full-screen
  blur pass; mobile games keep post-processing light.
  ([PC Gamer](https://www.pcgamer.com/uk/elden-ring-as-an-isometric-tilt-shift-adventure-game-looks-cosy),
  [80.lv: World of Tanks Blitz](https://80.lv/articles/setting-up-graphical-improvements-in-world-of-tanks-blitz/))

### Stylised water

- The colour comes from the depth (shallow to deep), often in a few flat bands; foam follows the
  shore and collars whatever stands in the water; waves are a few sines; the specular is stepped.
  ([Alexander Ameye: stylised water](http://ameye.dev/notes/stylized-water-shader),
  [Godot shaders: toon water](https://godotshaders.com/shader/toon-water/),
  [80.lv: water for Nimue](https://80.lv/articles/how-to-build-stylized-water-shader-design-implementation-for-nimue))
  Hand-drawn shorelines keep the foam thin.
- *Survival Kids* drives its foam and wakes from a precomputed distance to the coast, rather than
  from the depth buffer.

## 2. What the village does

- **An island with six places only.** Every building opens a menu (the lighthouse is the Dex);
  trees, rocks, bushes and paths are the only other things on it. Fewer, bigger, more detailed
  shapes, each with its own silhouette and colour.
- **Baked light in the vertex colours**, computed once when the meshes are built: the ground and
  the sea are fine grids (50 cm and 1 m) whose vertices store the sunlight in their alpha, from
  five rays towards the sun's disc (a soft penumbra that widens with distance) and a 3x3 blur;
  everything gets ambient occlusion towards the ground and contact shadows at the foot of what
  stands on it. Zero cost per frame. The sun is high in the west, slightly in front, so fronts
  are lit and shadows fall to the east, where they show; the moon uses the same direction.
- **Bevelled boxes** for walls, planks, beams and crates; thick roof slabs with rows of tiles and a
  ridge cap; windows with frames, sills and glazing bars.
- **Water from precomputed distances**, like *Survival Kids*: each sea vertex stores the depth,
  the closeness to the shore and the closeness to pilings, the arena, the boat and rocks. The
  shader draws three depth bands, thin foam lines running out from the beach, foam collars, a
  sky reflection at grazing angles and stepped glints.
- **A light grade** in the shader (a bit more saturation, warm lights, cool shadows) and a
  **vignette** drawn by Compose over the map: they frame the island like Croakwood's darkened
  edges and keep the HUD readable, for no GPU cost.
- **Not done**: tilt-shift and bloom (full-screen passes the reference phone can't afford while
  the village already uses most of its GPU), and real-time shadows (the scene doesn't move).
