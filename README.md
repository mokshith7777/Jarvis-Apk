# Walk-In Studio Template

A mobile-first interactive 3D studio experience built with Vanilla Three.js, GSAP when available, and WebAudio.

## Experience
- Black monogram preloader and ember transition
- Hallway entry gate with ON AIR sign, artwork, knock interaction, synthesized knock audio, and swinging door
- Single WebGL canvas with room, lounge, workstation, canvas wall, bookcase, and Zone B
- Drag-look navigation, camera station transitions, floor hotspots, mobile navigation
- Day/night lighting toggle
- Blobo violet orb and multi-step intake card
- Three workstation displays with safe preview fallback and new-tab controls
- Drawing canvas with color swatches, eraser, clear, and exit
- Shelf demos: magic cards, puzzle cube, 3D printer, MIDI pads, mini cars
- Piano tone demo, live digital clock, ember particles

## Performance and safety
- One renderer, pixel ratio capped at 2 (1.35 on mobile)
- Touch-first interaction and HUD pointer isolation
- CDN fallback for Three.js
- Iframe embedding is not assumed; previews fall back to local cards

## Run
Open the website on GitHub Pages. The project is static and does not require a backend.

## Validation
GitHub Actions checks HTML assets, workflow syntax, JavaScript syntax, HTTP staging, and runs a Chromium smoke test before deployment.
