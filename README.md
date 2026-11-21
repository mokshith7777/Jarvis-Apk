# JARVIS PRO — Product Landing Page

A single-file promotional landing page for the experimental JARVIS Android AI assistant.

## Website
- `index.html` — responsive product landing page, animated metallic reactor, feature overview, launch-price presentation, security notice, and APK release link.
- No build step or local asset bundle is required.
- Animation respects `prefers-reduced-motion`.

## Product claims
Content is based on the Android project documentation:
- Chat, voice input and text-to-speech
- Configurable hosted AI providers and local model endpoints
- Optional Termux worker
- User-driven Android app/screen workflows
- Local task history, settings and optional indexing
- Optional Telegram and Home Assistant integrations

Feature availability depends on device, Android version, permissions, and configuration. The app is experimental; the current documented public version is v3.3.1 and its APK is debug-signed for testing.

## Pricing presentation
The page displays the requested ₹49 standard price and ₹19 first-week launch offer. Checkout/payment confirmation is deliberately not enabled. A trusted adult-managed payment account and secure checkout/verification implementation must be set up before accepting payments; do not publish private payment details in static site source.

## Deploy
GitHub Pages workflow: `.github/workflows/pages.yml`.
Set repository **Settings → Pages → Build and deployment → Source** to **GitHub Actions** if Pages is not enabled, then check the Actions run.

## Source APK and documentation
- [Android repository](https://github.com/mokshith7777/Jarvis-android-)
- [Releases](https://github.com/mokshith7777/Jarvis-android-/releases)
- [Security notes](https://github.com/mokshith7777/Jarvis-android-/blob/main/SECURITY.md)
