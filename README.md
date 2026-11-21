# JARVIS PRO — Interactive Product Storefront

A mobile-first single-file product showcase for the experimental JARVIS Android AI companion.

## Experience
- Procedural interactive 3D core rendered with Canvas (drag/touch to rotate, click to pulse/change mode)
- Cinematic responsive hero and animated reveal-on-scroll sections
- Interactive feature cards with expanded explanations and setup/use-case notes
- Scenario tabs for voice, AI providers, local tooling and connected integrations (website simulation only)
- Feature-card tilt interactions on pointer devices
- FAQ accordion
- Reduced-motion accessibility support
- Mobile-responsive layouts and adaptive visual canvas
- No downloaded 3D model or image assets required

## Product claims
Content is based on the Android project documentation:
- Chat, voice input and text-to-speech
- Configurable hosted AI providers and local model endpoints
- Optional Termux worker
- User-driven Android app/screen workflows
- Local task history, settings and optional indexing
- Optional Telegram and Home Assistant integrations

Feature availability depends on device, Android version, permissions and configuration. The app is experimental; the documented public build is a test build and its release signing/readiness must be verified before distribution.

## Pricing presentation
The page displays ₹49 standard price and ₹19 first-week launch offer. Checkout is a presentation-only CTA; no payment processing or automatic APK delivery is active. A trusted adult should manage payment setup, and a secure checkout/payment-verification flow must be implemented before sales.

## Deploy
GitHub Pages workflow: `.github/workflows/pages.yml`.
Set repository **Settings → Pages → Build and deployment → Source** to **GitHub Actions** if Pages is not enabled, then check the Actions run.

## Source APK and documentation
- [Android repository](https://github.com/mokshith7777/Jarvis-android-)
- [Releases](https://github.com/mokshith7777/Jarvis-android-/releases)
- [Security notes](https://github.com/mokshith7777/Jarvis-android-/blob/main/SECURITY.md)
