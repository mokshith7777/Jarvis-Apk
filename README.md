# JARVIS PRO — Cinematic Phase 1

The active GitHub Pages site is a scene-first WebGL prototype, not the product storefront. It demonstrates a scroll-driven armor reveal, modular fracture, camera flight, and reactor close-up.

## Current experience
- Fixed full-page Three.js canvas with a procedural hangar, armor, fog, lighting, particles, bloom and film effects.
- Five scroll chapters: Awaken, Approach, Fracture, Flight, Reactor.
- Procedural armor is a placeholder; no photoreal suit or cinematic video asset is included.
- Custom GLB/GLTF models can be loaded from a same-origin file through the `?model=` parameter or the browser console API.
- Audio is synthesized only after the visitor enables it; it defaults off.
- Mobile rendering is capped and reduced-motion preferences are supported.
- A fallback is shown if WebGL initialization or the graphics context fails.

## Files
- `index.html`: minimal HUD, fixed canvas and scroll chapters.
- `styles/cinematic.css`: overlay, responsive and reduced-motion styles.
- `src/main.js`: Three.js scene, scroll choreography, audio and context handling.
- `src/armor.js`: procedural armor geometry and fracture vectors.
- `CINEMATIC_PHASE1.md`: scene and architecture notes.
- `archive/storefront-v1.html`: the older product storefront, retained for reference and not deployed.
- `scripts/validate_site.py`: static HTML, asset, accessibility and scene-integrity checks.

## Release status
Deployment and source-integrity checks can pass while the visual design is still pending review. The requested realistic cinematic, video-like full-page environment is **not yet accepted**: the current environment is procedural shader/3D artwork, not an actual cinematic video or photoreal scene asset. A supplied visual reference or recording should guide the next art pass.

The storefront product copy, price, setup guide, payment checkout and APK delivery are not present in this Phase 1 page. The archived storefront is reference material only.

## Deploy
GitHub Pages is deployed by `.github/workflows/pages.yml`. The workflow runs actionlint, site integrity checks, JavaScript/CSS parsing, Prettier checks on temporary copies, uploads the site artifact, deploys it, then requires HTTP 200 and an exact SHA-256 match of the live page to the checked-out `index.html`.
