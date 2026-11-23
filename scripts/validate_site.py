#!/usr/bin/env python3
from pathlib import Path
from html.parser import HTMLParser
import sys

root=Path(__file__).resolve().parents[1]
html=(root/'index.html').read_text(encoding='utf8')
errors=[]

class Audit(HTMLParser):
 def __init__(self):
  super().__init__(); self.ids=[]; self.hrefs=[]; self.scripts=[]; self.sections=[]; self.h1=0
 def handle_starttag(self,tag,attrs):
  a=dict(attrs)
  if a.get('id'): self.ids.append(a['id'])
  if tag=='a' and a.get('href'): self.hrefs.append(a['href'])
  if tag=='script' and a.get('src'): self.scripts.append(a['src'])
  if tag=='section' and a.get('id'): self.sections.append(a['id'])
  if tag=='h1': self.h1+=1

p=Audit(); p.feed(html)
for d in sorted(set(x for x in p.ids if p.ids.count(x)>1)): errors.append('duplicate id: '+d)
for href in p.hrefs:
 if href.startswith('#') and href[1:] not in p.ids: errors.append('broken anchor: '+href)
for src in p.scripts:
 if src.startswith('./') and not (root/src[2:]).exists(): errors.append('missing script: '+src)
for req in ['entry','features','setup','pricing','faq','room-world','enter-room','object-panel']:
 if req not in p.ids: errors.append('missing required id: '+req)
if p.h1!=1: errors.append('expected exactly one h1')
if len(p.sections)!=5: errors.append('expected 5 chapter sections, found '+str(len(p.sections)))
if not (root/'src/room.js').is_file(): errors.append('room runtime missing')
if not (root/'favicon.svg').is_file(): errors.append('favicon missing')
if not (root/'styles/room.css').is_file(): errors.append('room CSS missing')
if 'three@0.180.0' not in html: errors.append('Three.js import map missing')
if errors:
 print('SITE VALIDATION FAILED')
 print('\n'.join(' - '+e for e in errors))
 sys.exit(1)
print('Site audit passed: '+str(len(p.ids))+' IDs, 5 chapters, '+str(len(p.hrefs))+' links.')
