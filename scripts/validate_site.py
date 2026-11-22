#!/usr/bin/env python3
"""Static integrity checks for the JARVIS PRO storefront and its inline interactions."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / "index.html").read_text(encoding="utf-8")
errors = []

class Audit(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids = []
        self.hrefs = []
        self.srcs = []
        self.alt_missing = []
        self.landmarks = {"header": 0, "main": 0, "footer": 0}
        self.h1 = 0
        self.sections = []
        self.buttons = []
        self.button_stack = []
        self.links = []
        self.link_stack = []
        self.controls = []
        self.labels_for = set()
        self.label_depth = 0
        self.aria_refs = []
        self.inline_handlers = []
        self.javascript_urls = []
        self.inline_scripts = []
        self.importmaps = []
        self.styles = []
        self.script_active = False
        self.importmap_active = False
        self.style_active = False
        self.script_buffer = []
        self.importmap_buffer = []
        self.style_buffer = []

    def handle_starttag(self, tag, raw):
        a = dict(raw)
        if "id" in a:
            self.ids.append(a["id"])
        if tag in self.landmarks:
            self.landmarks[tag] += 1
        if tag == "h1":
            self.h1 += 1
        if tag == "section":
            self.sections.append(a.get("id", ""))
        if tag == "label":
            self.label_depth += 1
            if a.get("for"):
                self.labels_for.add(a["for"])
        if tag in ("input", "select", "textarea"):
            self.controls.append({"tag": tag, "attrs": a, "inside_label": self.label_depth > 0})
        if tag == "button":
            b = {"attrs": a, "text": ""}
            self.buttons.append(b)
            self.button_stack.append(b)
        if tag == "a":
            link = {"attrs": a, "text": ""}
            self.links.append(link)
            self.link_stack.append(link)
            self.hrefs.append(a.get("href", ""))
        if tag == "img" and "alt" not in a:
            self.alt_missing.append(a)
        for k, v in raw:
            k = (k or "").lower()
            v = v or ""
            if k.startswith("on") and len(k) > 2:
                self.inline_handlers.append((tag, k))
            if k in ("href", "src", "action") and v.lower().startswith("javascript:"):
                self.javascript_urls.append(v)
        for k in ("aria-labelledby", "aria-describedby", "aria-controls"):
            if a.get(k):
                self.aria_refs.extend(a[k].split())
        for k in ("href", "src"):
            if a.get(k):
                self.srcs.append(a[k])
        if tag == "script":
            t = a.get("type", "").lower()
            if not a.get("src") and t == "importmap":
                self.importmap_active = True
                self.importmap_buffer = []
            elif not a.get("src"):
                self.script_active = True
                self.script_buffer = []
        if tag == "style":
            self.style_active = True
            self.style_buffer = []

    def handle_endtag(self, tag):
        if tag == "button" and self.button_stack:
            self.button_stack.pop()
        if tag == "a" and self.link_stack:
            self.link_stack.pop()
        if tag == "label" and self.label_depth:
            self.label_depth -= 1
        if tag == "script":
            if self.script_active:
                self.inline_scripts.append("".join(self.script_buffer))
                self.script_active = False
            if self.importmap_active:
                self.importmaps.append("".join(self.importmap_buffer))
                self.importmap_active = False
        if tag == "style" and self.style_active:
            self.styles.append("".join(self.style_buffer))
            self.style_active = False

    def handle_data(self, data):
        if self.script_active:
            self.script_buffer.append(data)
        if self.importmap_active:
            self.importmap_buffer.append(data)
        if self.style_active:
            self.style_buffer.append(data)
        for b in self.button_stack:
            b["text"] += data
        for a in self.link_stack:
            a["text"] += data

audit = Audit()
audit.feed(HTML)
audit.close()

def check(ok, msg):
    if not ok:
        errors.append(msg)

duplicates = sorted({x for x in audit.ids if audit.ids.count(x) > 1})
id_set = set(audit.ids)
check(not duplicates, "Duplicate IDs: " + ", ".join(duplicates))
check(audit.h1 == 1, f"Expected exactly one H1, found {audit.h1}")
check(all(audit.landmarks[x] == 1 for x in audit.landmarks), "Expected exactly one header, main, and footer")
check({"story", "features", "experience", "connectlab", "integrations", "setup", "pricing", "faq"}.issubset(set(audit.sections)), "Original storefront content sections missing")
check(not audit.alt_missing, "Image missing alt text")
check(not audit.inline_handlers, "Inline event handlers found")
check(not audit.javascript_urls, "javascript: URL found")
check(not (set(audit.aria_refs) - id_set), "Broken ARIA references: " + ", ".join(sorted(set(audit.aria_refs) - id_set)))

for b in audit.buttons:
    a = b["attrs"]
    name = a.get("aria-label") or a.get("aria-labelledby") or a.get("title") or b["text"].strip()
    check(bool(name), "Unnamed button found")
for link in audit.links:
    a = link["attrs"]
    name = a.get("aria-label") or a.get("aria-labelledby") or a.get("title") or link["text"].strip()
    check(bool(name), "Unnamed link found: " + a.get("href", ""))
    if a.get("target", "").lower() == "_blank":
        rel = set(a.get("rel", "").lower().split())
        check("noopener" in rel, "External target=_blank link missing noopener: " + a.get("href", ""))

for control in audit.controls:
    a = control["attrs"]
    if a.get("type", "").lower() == "hidden":
        continue
    name = a.get("aria-label") or a.get("aria-labelledby") or a.get("title")
    check(bool(name or control["inside_label"] or a.get("id") in audit.labels_for), "Unlabelled " + control["tag"] + " control")

for href in audit.hrefs:
    u = urlsplit(href)
    if u.fragment and not u.path and u.fragment not in id_set:
        check(False, "Broken internal link #" + u.fragment)
for ref in audit.srcs:
    u = urlsplit(ref)
    if u.scheme or u.netloc or ref.startswith("#") or not u.path:
        continue
    path = (ROOT / unquote(u.path).lstrip("/")).resolve()
    check(path.is_relative_to(ROOT.resolve()), "Asset escapes repo: " + ref)
    check(path.exists(), "Missing local asset: " + ref)

# Check literal ID selectors used by executable inline JavaScript against the DOM.
for script in audit.inline_scripts:
    refs = set(re.findall(r"querySelector(?:All)?\(\s*['\"\x60]#([A-Za-z0-9_-]+)['\"\x60]", script))
    refs.update(re.findall(r"getElementById\(\s*['\"\x60]([A-Za-z0-9_-]+)['\"\x60]", script))
    check(not (refs - id_set), "Inline JavaScript references missing DOM IDs: " + ", ".join(sorted(refs - id_set)))

for impmap in audit.importmaps:
    try:
        json.loads(impmap)
    except json.JSONDecodeError as exc:
        check(False, "Invalid import map JSON: " + str(exc))

check('id="armor-image"' in HTML and "iron-man-2020-suit-xf-1440x2560.jpg" in HTML, "Current externally hosted armor image markup/source missing")
check("armor-image" in HTML and "reactor-transition" in HTML, "Scroll zoom / reactor transition markup missing")
check("requestAnimationFrame(render)" in HTML and "armor-scroll" in HTML, "Scroll-driven animation code missing")
check("prefers-reduced-motion" in HTML, "Reduced motion handling missing")
check('.atmo-grid{display:none!important}' in HTML, 'Abstract background grid overlay must remain disabled')
check('body.cinematic-motion-ready .reveal{opacity:0}' in HTML, 'GSAP reveal CSS readiness gate missing')
check("document.querySelectorAll('.revealline').forEach(el=>observer.observe(el))" in HTML, 'Legacy observer must not animate GSAP reveal elements')
check('.reveal.visible' not in HTML and '.setup-station .reveal.visible' not in HTML, 'Legacy CSS reveal animation conflicts with GSAP')
check("JARVIS PRO" in HTML and "₹19" in HTML and "PRODUCT DETAILS" in HTML, "Storefront product/pricing content missing")
check("cinematic-final-pass" in HTML and "#cinematic-world" in HTML, "Cinematic background visibility layer missing")
check('referrerpolicy="no-referrer"' in HTML, "Armor image referrer policy missing")
check('id="sound-toggle"' in HTML and 'aria-pressed="false"' in HTML, "Sound toggle must exist and default to off")
check("enabled=false" in HTML and "toggle.addEventListener('click'" in HTML, "Sound must only initialize through explicit user interaction")
check(not re.search(r"<(?:audio|video)\b[^>]*autoplay", HTML, re.I), "Autoplay media is not allowed")
check('id="background-canvas"' in HTML and 'id="scene-canvas"' in HTML, "Continuous cinematic background canvases missing")
check('id="floatingnote"' in HTML and 'id="floattext"' in HTML, "Scroll chapter note UI missing")
coil_block = HTML.split('data-part="coils">', 1)[1].split("</div>", 1)[0] if 'data-part="coils">' in HTML else ""
check(coil_block.count("<i></i>") == 10, "Expected ten copper coil modules in reactor markup")
check('data-part="cage"' in HTML and 'class="cage-spoke s10"' in HTML, "Ten-spoke reactor cage markup missing")
check("Questions, answered." in HTML and "Bring JARVIS online." in HTML, "FAQ or setup guide content missing")

MAIN = (ROOT / "src/main.js").read_text(encoding="utf-8")
ARMOR = (ROOT / "src/armor.js").read_text(encoding="utf-8")
CSS = (ROOT / "styles/cinematic.css").read_text(encoding="utf-8")
check("scrollTrigger:{start:0,end:()=>Math.max(1,ScrollTrigger.maxScroll(window))" in MAIN, "Cinematic progress must track the full document scroll")
check("lerp:reduceMotion?1:mobile?.14:.12" in MAIN, "Lenis scroll interpolation must be tuned for responsive smoothness")
check("1-Math.exp(-24*delta)" in MAIN, "Cinematic camera progress must use frame-rate-independent damping")
check("scrub:reduceMotion?false:.25" in MAIN, "ScrollTrigger scrub should remain responsive without excessive lag")
check("document.body.classList.add('cinematic-motion-ready')" in MAIN and "gsap.utils.toArray('.reveal')" in MAIN, 'GSAP reveal owner must initialize only after runtime readiness')
check("gsap.utils.toArray('#features .feature, #experience .demo" not in MAIN, 'Duplicate GSAP reveal tweens must not animate the same elements')
check("document.body.classList.add('entry-locked')" not in MAIN, "Dead entry lock must not prevent scrolling")
check("reactorObject.visible=reactorReveal>.015" in MAIN, "3D reactor reveal visibility choreography missing")
check("webglcontextlost" in MAIN and "webglcontextrestored" in MAIN, "Three.js context loss/recovery handlers missing")
check("fallback.hidden=false" in MAIN and "renderer.domElement" in MAIN, "Three.js fallback overlay missing")
check("requestAnimationFrame(render)" in MAIN, "Three.js render loop missing")
check("for(let i=0;i<10;i++" in ARMOR and "copper-winding-" in ARMOR, "Ten-coil reactor detail missing")
check("prefers-reduced-motion" in CSS, "Source cinematic CSS lacks reduced-motion handling")
check("@media" in CSS, "Source cinematic CSS lacks responsive breakpoints")
check("import './armor.js'" not in MAIN and "from './armor.js'" in MAIN, "Local armor module import missing")

# Three.js must be wired into the deployed HTML and artifact, not source-only.
WORKFLOW = (ROOT / ".github/workflows/pages.yml").read_text(encoding="utf-8")
main_runtime_loaded = bool(re.search(r"<script[^>]+src=[\"'][^\"']*src/main\.js", HTML, re.I))
runtime_artifact = "cp -R src styles" in WORKFLOW
check(main_runtime_loaded, "Three.js module is not loaded by deployed index.html")
check(runtime_artifact, "Pages artifact does not include src/ and styles/ runtime files")
check('type="importmap"' in HTML and '"three":' in HTML and '"three/addons/"' in HTML, "Three.js import map missing/incomplete")
check('id="cinematic-world"' in HTML and 'id="progress-fill"' in HTML and 'id="chapter-number"' in HTML and 'id="chapter-name"' in HTML and 'id="hud-phase"' in HTML, "Three.js canvas/HUD DOM missing")
check('id="loading"' in HTML and 'id="fallback"' in HTML and 'id="load-progress"' in HTML, "Runtime loading/fallback DOM missing")
check("webglcontextrestored" in MAIN and "composer.setSize" in MAIN, "WebGL restoration does not resize postprocessing")
check("renderer.resetState()" in MAIN, "WebGL restoration does not reset renderer state")
check("window.__JARVIS_RUNTIME__" in MAIN and "status:'active'" in MAIN and "frames" in MAIN, "runtime execution marker/frame telemetry missing")
check('reducedMotion:reduceMotion' in MAIN, 'runtime motion telemetry references an undefined identifier')
check("contextRestorations" in MAIN and "status='context-lost'" in MAIN, "context loss/restoration runtime telemetry missing")
check("window.__JARVIS_RUNTIME__" in HTML and "module-load-timeout" in HTML, "module-load watchdog fallback telemetry missing")
SMOKE = ROOT / "scripts/runtime_smoke.cjs"
check(SMOKE.is_file(), "Browser runtime smoke script missing")
if SMOKE.is_file():
    SMOKE_TEXT = SMOKE.read_text(encoding="utf-8")
    check("WEBGL_lose_context" in SMOKE_TEXT and "contextRestorations" in SMOKE_TEXT, "Browser smoke does not verify context recovery")
    check("demo-tab" in SMOKE_TEXT and "devicefamily" in SMOKE_TEXT and "setup-reset" in SMOKE_TEXT, "Browser smoke misses core interactions")
    check('deviceFamily.scrollIntoViewIfNeeded()' in SMOKE_TEXT and 'firstReveal.scrollIntoViewIfNeeded()' in SMOKE_TEXT, 'Browser smoke must wait for scroll-reveal content before interacting')
    check('window.scrollY<100' in SMOKE_TEXT and "cs.visibility==='hidden'" in SMOKE_TEXT, 'Browser smoke must verify reveal reversibility')
check("Browser smoke for staged Pages artifact" in WORKFLOW and "runtime_smoke.cjs" in WORKFLOW, "Staged browser runtime smoke is not a CI gate")
check("Browser smoke for live Pages deployment" in WORKFLOW, "Live browser runtime smoke is not a CI gate")

# This is a warning, not a release-blocking test: cinematic realism requires human visual approval.
if not re.search(r"<video\b|\.(?:mp4|webm|mov)(?:[?#]|$)", HTML, re.I):
    print("VISUAL ACCEPTANCE PENDING: active scene is still procedural armor/hangar; a realistic full-page nature environment and exact reference matching require visual review.")

if errors:
    print("SITE AUDIT FAILED")
    for e in errors:
        print("FAIL:", e)
    sys.exit(1)
print(f"Storefront audit passed: {len(audit.ids)} IDs, {len(audit.sections)} sections, {len(audit.hrefs)} links, {len(audit.inline_scripts)} executable inline scripts, {len(audit.styles)} inline style blocks.")
