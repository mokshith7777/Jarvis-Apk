# JARVIS PRO Storefront

The active GitHub Pages site is the full JARVIS PRO product storefront, restored from the previous site content and rebuilt around the supplied Iron Man 2020 suit image.

## Cinematic entry
- The hero currently uses an externally hosted Iron Man 2020 suit image. Exact matching against the user's original reference set has not been verified.
- Scroll choreography zooms toward the chest area and transitions to a layered Arc Reactor graphic. The current active Three.js scene remains a procedural hangar/armor prototype, not the requested realistic nature environment.
- No video/training-video asset or autoplay media is used.
- Scroll reveal respects reduced-motion preferences.

## Visual acceptance status

- The abstract background grid overlay is disabled and scroll reveals now have one GSAP owner; reduced-motion and runtime-fallback states keep content visible.
- The requested realistic, full-page interactive 3D nature background is not yet integrated or visually verified. A separate Manus task is pending review; its output is not merged into this repository.
- The exact five armor/Arc Reactor reference images are not present in the accessible repository or Library files at audit time. Reattach them before claiming exact fidelity.
- Checkout/APK delivery remains presentation-only; no live payment or download fulfillment is active.

## Restored storefront content
- Product hero and product purpose
- Feature cards: Voice + Chat, AI Provider Choice, Local Tooling, Android Workflows, History + Settings, Connected Services
- Interactive demo scenarios
- Provider connection sandbox
- Ecosystem/integrations overview
- Android setup walkthrough and device-specific steps
- Product/launch pricing presentation and purchase status disclaimer
- FAQ and footer information

The checkout and APK delivery are still presentation-only and are clearly described as inactive; do not treat this page as a live payment or delivery system.

## Source image
Armor image attribution is shown on the site: ultraraw26 via HDQWalls. The source page is linked from the footer.

## Files
- `index.html`: full storefront, cinematic scroll reveal, restored content and interactions.
- `archive/storefront-v1.html`: preserved original storefront snapshot.
- `scripts/validate_site.py`: static integrity and content checks.
- `.github/workflows/pages.yml`: validation, Pages artifact upload, deploy and live SHA verification.

## Deploy
GitHub Pages is deployed by `.github/workflows/pages.yml`. The workflow runs actionlint and site checks, parses inline JavaScript, validates HTML/CSS formatting, uploads the site artifact, deploys, and requires HTTP 200 plus an exact SHA-256 match of the live page to the checked-out `index.html`.
