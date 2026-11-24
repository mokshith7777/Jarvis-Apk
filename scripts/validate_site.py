#!/usr/bin/env python3
from pathlib import Path
from html.parser import HTMLParser
import sys
root=Path(__file__).resolve().parents[1];html=(root/'index.html').read_text();js=(root/'src/room.js').read_text()
errors=[]
class Audit(HTMLParser):
 def __init__(self):super().__init__();self.ids=[];self.scripts=[];self.links=[]
 def handle_starttag(self,t,a):
  d=dict(a)
  if d.get('id'):self.ids.append(d['id'])
  if t=='script' and d.get('src'):self.scripts.append(d['src'])
  if t=='link' and d.get('href') and d.get('rel')=='stylesheet':self.links.append(d['href'])
p=Audit();p.feed(html)
if len(set(p.ids))!=len(p.ids):errors.append('duplicate IDs')
for f in p.scripts+p.links:
 if f.startswith('./') and not (root/f[2:]).exists():errors.append('missing asset '+f)
for x in ['world','preloader','door-prompt','intake','screen-modal','canvas-modal','prop-modal','light-mode','context-nav']:
 if x not in p.ids:errors.append('required element missing: '+x)
if 'bottom-nav' in p.ids:errors.append('legacy permanent navigation remains')
for required in ['buildRoom','buildDesk','buildPiano','buildBookcase','buildLighting','buildRings','knock','openIntake','openScreen','openBoard','buildReveal','SCREENS_CONFIG']:
 if required not in js:errors.append('runtime function/config missing: '+required)
for banned in ['cdn.jsdelivr.net','unpkg.com','esm.sh','example.com','example.org','developer.mozilla.org']:
 if banned in js:errors.append('external placeholder/runtime CDN detected: '+banned)
if not (root/'favicon.svg').is_file():errors.append('favicon missing')
if errors:print('FAILED\\n'+'\\n'.join(errors));sys.exit(1)
print('Immersive studio static validation passed.')
