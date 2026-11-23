# JARVIS PRO · Interactive Room Storefront

Fresh room-first storefront. The persistent Three.js environment is the experience; page chapters guide the camera and product story.

## Features
- Persistent Three.js command-room scene with scroll-linked camera progression.
- Interactive room hotspots and product object.
- Responsive chapter navigation and adaptive renderer quality.
- Reduced-motion support and WebGL fallback.
- Accurate experimental-product messaging; checkout is not yet connected.

## Preview
`python3 -m http.server 4173` then visit `http://127.0.0.1:4173`.

## Validate
`python3 scripts/validate_site.py`
`node --check src/room.js`
`node --check scripts/runtime_smoke.cjs`

Pushes to `main` run validation and browser smoke before Pages deployment.
