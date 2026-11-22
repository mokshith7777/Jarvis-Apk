#!/usr/bin/env python3
"""Static integrity checks for the restored JARVIS PRO cinematic storefront."""
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
HTML = (ROOT / "index.html").read_text(encoding="utf-8")
errors = []

class Audit(HTMLParser):
    def __init__(self):
        super().__init__()
        self.ids=[]; self.hrefs=[]; self.srcs=[]; self.alt_missing=[]
        self.landmarks={"header":0,"main":0,"footer":0}
        self.h1=0; self.sections=[]; self.stack=[]; self.buttons=[]; self.button_stack=[]
        self.aria_refs=[]; self.inline_handlers=[]; self.javascript_urls=[]
    def handle_starttag(self,tag,raw):
        a=dict(raw)
        if "id" in a:self.ids.append(a["id"])
        if tag in self.landmarks:self.landmarks[tag]+=1
        if tag=="h1":self.h1+=1
        if tag=="section":
            self.sections.append(a.get("id",""));self.stack.append(a.get("id",""))
        if tag=="button":
            b={"attrs":a,"text":""};self.buttons.append(b);self.button_stack.append(b)
        if tag=="img" and "alt" not in a:self.alt_missing.append(a)
        for k,v in raw:
            k=(k or "").lower();v=v or ""
            if k.startswith("on") and len(k)>2:self.inline_handlers.append((tag,k))
            if k in ("href","src","action") and v.lower().startswith("javascript:"):self.javascript_urls.append(v)
        for k in ("aria-labelledby","aria-describedby","aria-controls","for"):
            if a.get(k):self.aria_refs.extend(a[k].split())
        if tag=="a":self.hrefs.append(a.get("href",""))
        for k in ("href","src"):
            if a.get(k):self.srcs.append(a[k])
    def handle_endtag(self,tag):
        if tag=="button" and self.button_stack:self.button_stack.pop()
        if tag=="section" and self.stack:self.stack.pop()
    def handle_data(self,data):
        for b in self.button_stack:b["text"]+=data

audit=Audit();audit.feed(HTML);audit.close()
def check(ok,msg):
    if not ok:errors.append(msg)
duplicates=sorted({x for x in audit.ids if audit.ids.count(x)>1})
check(not duplicates,"Duplicate IDs: "+", ".join(duplicates))
check(audit.h1==1,f"Expected exactly one H1, found {audit.h1}")
check(all(audit.landmarks[x]==1 for x in audit.landmarks),"Expected exactly one header, main, and footer")
check({"story","features","experience","connectlab","integrations","setup","pricing","faq"}.issubset(set(audit.sections)),"Original storefront content sections missing")
check(not audit.alt_missing,"Image missing alt text")
check(not audit.inline_handlers,"Inline event handlers found")
check(not audit.javascript_urls,"javascript: URL found")
check(not (set(audit.aria_refs)-set(audit.ids)),"Broken ARIA references: "+", ".join(sorted(set(audit.aria_refs)-set(audit.ids))))
for b in audit.buttons:
    a=b["attrs"];name=a.get("aria-label") or a.get("aria-labelledby") or a.get("title") or b["text"].strip()
    check(bool(name),"Unnamed button found")
for href in audit.hrefs:
    u=urlsplit(href)
    if u.fragment and not u.path and u.fragment not in audit.ids:check(False,"Broken internal link #"+u.fragment)
for ref in audit.srcs:
    u=urlsplit(ref)
    if u.scheme or u.netloc or ref.startswith("#") or not u.path:continue
    path=(ROOT/unquote(u.path).lstrip("/")).resolve()
    check(path.is_relative_to(ROOT.resolve()),"Asset escapes repo: "+ref)
    check(path.exists(),"Missing local asset: "+ref)
check("iron-man-2020-suit-xf-1440x2560.jpg" in HTML,"Supplied Iron Man reference image source not present")
check("armor-image" in HTML and "reactor-transition" in HTML,"Scroll zoom / reactor transition markup missing")
check("requestAnimationFrame(render)" in HTML and "armor-scroll" in HTML,"Scroll-driven animation code missing")
check("prefers-reduced-motion" in HTML,"Reduced motion handling missing")
check(not re.search(r"<video\b",HTML,re.I),"Unexpected video element; image-based reveal is intended")
check("JARVIS PRO" in HTML and "₹19" in HTML and "PRODUCT DETAILS" in HTML,"Storefront product/pricing content missing")
check("Questions, answered." in HTML and "Bring JARVIS online." in HTML,"FAQ or setup guide content missing")
if errors:
    print("SITE AUDIT FAILED")
    for e in errors:print("FAIL:",e)
    sys.exit(1)
print(f"Storefront audit passed: {len(audit.ids)} unique IDs, {len(audit.sections)} sections, {len(audit.hrefs)} links.")
