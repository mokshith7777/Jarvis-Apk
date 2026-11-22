#!/usr/bin/env python3
"""Static integrity checks for the JARVIS Phase 1 Pages site."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import json
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / "index.html").read_text(encoding="utf-8")
CSS = (ROOT / "styles/cinematic.css").read_text(encoding="utf-8")
MAIN_JS = (ROOT / "src/main.js").read_text(encoding="utf-8")
ARMOR_JS = (ROOT / "src/armor.js").read_text(encoding="utf-8")
WORKFLOW = (ROOT / ".github/workflows/pages.yml").read_text(encoding="utf-8")
README = (ROOT / "README.md").read_text(encoding="utf-8")
errors = []
warnings = []

class PageAudit(HTMLParser):
    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.ids = []
        self.hrefs = []
        self.buttons = []
        self.controls = []
        self.images_missing_alt = []
        self.inline_handlers = []
        self.javascript_urls = []
        self.blank_links_missing_rel = []
        self.local_refs = []
        self.inline_scripts = []
        self.sections = []
        self.section_stack = []
        self.landmarks = {"header": 0, "main": 0, "footer": 0}
        self.h1 = 0
        self.h2 = 0
        self.button_stack = []
        self.importmap_active = False
        self.importmap_parts = []
        self.inline_active = False
        self.aria_refs = []

    def handle_starttag(self, tag, raw_attrs):
        attrs = dict(raw_attrs)
        if "id" in attrs:
            self.ids.append(attrs["id"])
        if tag in self.landmarks:
            self.landmarks[tag] += 1
        if tag == "h1":
            self.h1 += 1
        if tag == "h2":
            self.h2 += 1
            if self.section_stack:
                self.section_stack[-1]["h2"] += 1
        if tag == "section":
            section = {"chapter": attrs.get("data-chapter"), "h2": 0}
            self.sections.append(section)
            self.section_stack.append(section)
        if tag == "button":
            entry = {"attrs": attrs, "text": ""}
            self.buttons.append(entry)
            self.button_stack.append(entry)
        if tag in {"input", "select", "textarea"}:
            if tag != "input" or attrs.get("type", "").lower() != "hidden":
                self.controls.append(attrs)
        if tag == "img" and "alt" not in attrs:
            self.images_missing_alt.append(attrs)
        for key, value in raw_attrs:
            key = (key or "").lower()
            value = value or ""
            if key.startswith("on") and len(key) > 2:
                self.inline_handlers.append((tag, key))
            if key in {"href", "src", "action"} and value.strip().lower().startswith("javascript:"):
                self.javascript_urls.append((tag, key, value))
        for attr in ("aria-labelledby", "aria-describedby", "aria-controls", "for"):
            if attrs.get(attr):
                self.aria_refs.extend(attrs[attr].split())
        if tag == "a":
            href = attrs.get("href", "")
            self.hrefs.append(href)
            if attrs.get("target", "").lower() == "_blank" and not re.search(r"\b(?:noopener|noreferrer)\b", attrs.get("rel", ""), re.I):
                self.blank_links_missing_rel.append(href)
        for key in ("href", "src"):
            value = attrs.get(key)
            if value:
                self.local_refs.append(value)
        if tag == "script":
            if attrs.get("type", "").lower() == "importmap":
                self.importmap_active = True
            elif not attrs.get("src"):
                self.inline_active = True

    def handle_endtag(self, tag):
        if tag == "button" and self.button_stack:
            self.button_stack.pop()
        if tag == "section" and self.section_stack:
            self.section_stack.pop()
        if tag == "script":
            self.importmap_active = False
            self.inline_active = False

    def handle_data(self, data):
        for button in self.button_stack:
            button["text"] += data
        if self.importmap_active:
            self.importmap_parts.append(data)
        if self.inline_active:
            self.inline_scripts.append(data)

page = PageAudit()
page.feed(HTML)
page.close()

def fail(message):
    errors.append(message)

def check(condition, message):
    if not condition:
        fail(message)

duplicate_ids = sorted({value for value in page.ids if page.ids.count(value) > 1})
check(not duplicate_ids, "Duplicate HTML id(s): " + ", ".join(duplicate_ids))
check(page.h1 == 1, f"Expected exactly one h1; found {page.h1}")
check(all(page.landmarks[name] == 1 for name in page.landmarks), "Expected exactly one header, main and footer landmark")
check(len(page.sections) == 5, f"Expected five story chapters; found {len(page.sections)}")
check(all(section["chapter"] and section["h2"] == 1 for section in page.sections), "Every story section must have a data-chapter and exactly one h2")
check(not page.images_missing_alt, "Image(s) missing alt attributes")
check(not page.inline_handlers, "Unsafe inline event handler(s): " + repr(page.inline_handlers))
check(not page.javascript_urls, "javascript: URL(s) found")
check(not page.blank_links_missing_rel, "target=_blank link(s) missing noopener/noreferrer")
check(not (set(page.aria_refs) - set(page.ids)), "ARIA/label reference(s) missing: " + ", ".join(sorted(set(page.aria_refs) - set(page.ids))))
for button in page.buttons:
    attrs = button["attrs"]
    accessible_name = attrs.get("aria-label") or attrs.get("aria-labelledby") or attrs.get("title") or button["text"].strip()
    check(bool(accessible_name), "Button without accessible name")
for control in page.controls:
    accessible_name = control.get("aria-label") or control.get("aria-labelledby") or control.get("title") or control.get("name")
    check(bool(accessible_name), "Form control without accessible name: " + repr(control))
for href in page.hrefs:
    parsed = urlsplit(href)
    if not parsed.path and parsed.fragment:
        check(parsed.fragment in page.ids, f"Anchor target not found: #{parsed.fragment}")
for ref in page.local_refs:
    parsed = urlsplit(ref)
    if parsed.scheme or parsed.netloc or ref.startswith("#") or not parsed.path:
        continue
    local_path = ROOT / unquote(parsed.path).lstrip("/")
    check(local_path.resolve().is_relative_to(ROOT.resolve()), f"Asset path escapes repository: {ref}")
    check(local_path.exists(), f"Local HTML asset does not exist: {ref}")
for source_path, source in (("src/main.js", MAIN_JS), ("src/armor.js", ARMOR_JS)):
    for relative in re.findall(r"\bfrom\s+['\"](\.[^'\"]+)['\"]", source):
        check((ROOT / source_path).parent.joinpath(relative).resolve().exists(), f"Local JavaScript import missing: {source_path} -> {relative}")
all_js = MAIN_JS + "\n" + "\n".join(page.inline_scripts)
js_ids = set(re.findall(r"getElementById\(\s*['\"]([A-Za-z0-9_-]+)['\"]", all_js))
js_ids.update(re.findall(r"querySelector(?:All)?\(\s*['\"]#([A-Za-z0-9_-]+)['\"]", all_js))
check(not (js_ids - set(page.ids)), "JavaScript references missing DOM id(s): " + ", ".join(sorted(js_ids - set(page.ids))))
check(re.search(r"#cinematic-world\s*\{[^}]*position\s*:\s*fixed", CSS, re.S), "Cinematic canvas is not fixed full-page")
check(re.search(r"@media\s*\(\s*max-width\s*:\s*700px\s*\)", CSS), "Mobile responsive breakpoint missing")
check(re.search(r"prefers-reduced-motion", CSS, re.I), "Reduced-motion CSS missing")
check("prefers-reduced-motion" in MAIN_JS and "state.progress=state.target" in MAIN_JS and "smoothWheel:!reduceMotion" in MAIN_JS, "Reduced-motion JS does not make scroll progression immediate")
check("ShaderMaterial" in MAIN_JS and "FogExp2" in MAIN_JS and "uTime" in MAIN_JS, "Atmospheric shader/fog animation missing")
check("ScrollTrigger" in MAIN_JS and "CatmullRomCurve3" in MAIN_JS, "Scroll-driven camera chapter choreography missing")
check("webglcontextlost" in MAIN_JS and "webglcontextrestored" in MAIN_JS, "WebGL context-loss/restore handling missing")
check('id="fallback"' in HTML and "fallback.hidden=false" in MAIN_JS, "WebGL fallback UI missing")
check('id="progress-fill"' in HTML and "progressFill.style.width" in MAIN_JS, "Progress HUD missing")
check('aria-pressed="false"' in HTML, "Audio control must default off")
check(len(re.findall(r"\bstartAudio\s*\(", MAIN_JS)) == 2 and "audioButton.addEventListener('click'" in MAIN_JS, "Audio must be click-to-enable, not autoplay")
check(not re.search(r"<audio\b[^>]*\bautoplay", HTML, re.I), "Autoplay audio found")
check("origin!==location.origin" in MAIN_JS and r"\.(glb|gltf)$" in MAIN_JS, "Query model loader must restrict model assets to same-origin GLB/GLTF files")
maps = re.findall(r'<script type="importmap">([\s\S]*?)</script>', HTML)
check(len(maps) == 1, f"Expected one import map, found {len(maps)}")
if len(maps) == 1:
    try:
        importmap = json.loads(maps[0])
        imports = importmap.get("imports", {})
        check("three@0.170.0/" in imports.get("three", "") and "three@0.170.0/" in imports.get("three/addons/", ""), "Three.js import map version must be pinned")
    except (ValueError, AttributeError):
        fail("Import map JSON is invalid")
uses = re.findall(r"^\s*uses:\s*([^\s#]+)", WORKFLOW, re.M)
check(uses and all(re.search(r"@[0-9a-f]{40}$", action) for action in uses), "GitHub Actions must be pinned to full commit SHAs")
check("actionlint" in WORKFLOW and "prettier@3.5.3" in WORKFLOW, "Workflow YAML/actionlint or parser checks missing")
check(re.search(r"path:\s*\.\s*$", WORKFLOW, re.M) and re.search(r"include-hidden-files:\s*true", WORKFLOW), "Pages artifact must upload root and hidden files")
check("contents: read" in WORKFLOW and "pages: write" in WORKFLOW and "id-token: write" in WORKFLOW, "Pages workflow permissions missing or overly broad")
check((ROOT / ".nojekyll").is_file(), ".nojekyll file missing")
check("Cinematic Phase 1" in README and "storefront" in README.lower(), "README must identify current Phase 1 and archived storefront")

# Balance CSS braces outside comments and quoted strings.
depth = 0
quote = None
comment = False
i = 0
while i < len(CSS):
    if comment:
        if CSS.startswith("*/", i):
            comment = False
            i += 2
            continue
        i += 1
        continue
    if quote:
        if CSS[i] == "\\":
            i += 2
            continue
        if CSS[i] == quote:
            quote = None
        i += 1
        continue
    if CSS.startswith("/*", i):
        comment = True
        i += 2
        continue
    if CSS[i] in ("'", '"'):
        quote = CSS[i]
        i += 1
        continue
    if CSS[i] == "{":
        depth += 1
    elif CSS[i] == "}":
        depth -= 1
        if depth < 0:
            fail("CSS unmatched closing brace")
    i += 1
check(depth == 0 and not quote and not comment, "CSS brace/string/comment balance invalid")

if not re.search(r"<video\b", HTML, re.I) and not re.search(r"\.(?:mp4|webm|mov)\b", HTML + CSS, re.I):
    warnings.append("No cinematic video asset is present; current scene is procedural 3D/shader art, so visual acceptance remains pending.")
if not re.search(r"\b(?:price|₹)\b", HTML, re.I) or not re.search(r"\bsetup\b", HTML, re.I):
    warnings.append("Storefront price/CTA/setup guide is absent from Phase 1 and must not be considered a product release.")
if errors:
    print("SITE AUDIT FAILED")
    for error in errors:
        print("FAIL:", error)
    sys.exit(1)
print(f"Site audit passed: {len(page.ids)} unique ids, {len(page.sections)} chapter sections, {len(js_ids)} JS DOM id references, {len(page.local_refs)} asset references.")
for warning in warnings:
    print("WARN:", warning)
