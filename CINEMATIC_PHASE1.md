# JARVIS Cinematic Phase 1

Phase 1 is a scene-first WebGL prototype. Product copy and storefront placement are intentionally deferred until the visual sequence is approved.

## Scene sequence
- 01 Awaken: dark engineering hangar, atmospheric haze, armor on a circular platform.
- 02 Approach: scroll-linked camera spline pushes toward the armor.
- 03 Fracture: modular procedural armor groups separate on their own vectors.
- 04 Flight: camera advances through the exploded assembly.
- 05 Reactor: close-up of the glowing multi-ring Arc Reactor.

Reverse scrolling scrubs the same scene state. The canvas remains fixed; story sections provide scroll length only.

## Architecture
- `index.html`: minimal HUD, fixed canvas, scroll chapters and import map.
- `styles/cinematic.css`: overlay and responsive presentation.
- `src/main.js`: Three.js scene, Lenis, GSAP ScrollTrigger, camera choreography, lighting, fog, particles, custom film/chromatic pass, bloom, optional desktop depth-of-field, audio opt-in and visibility pause.
- `src/armor.js`: modular procedural suit assembly and explosion vectors.
- `archive/storefront-v1.html`: pre-Phase-1 storefront retained for reference.

## Hot-swappable armor assets
Use `window.JARVIS3D.loadArmor(url, {scale, position})` from the browser console or a future UI. GLTFLoader is configured with Draco and KTX2 support. A custom model can be loaded using `?model=path/to/model.glb`. For best fit, author the GLB centered near the origin, facing +Z, with feet near Y=0 and a height around 2.5 world units; adjust scale/position in options if needed.

## Quality and limitations
The initial armor is procedural geometry, not a photoreal licensed character model. It is a placeholder rig used to validate lighting, scene composition, scroll choreography and modular separation. Custom GLB assets are expected to replace it after the Phase 1 visual review. External CDN modules keep the GitHub Pages repo lightweight.

Audio is synthesized only after visitors explicitly enable it. Mobile uses capped pixel ratio and lower post-processing cost. Reduced-motion preference is respected.
