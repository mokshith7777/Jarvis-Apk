#!/usr/bin/env python3
from pathlib import Path
from html.parser import HTMLParser
import sys
root=Path(__file__).resolve().parents[1]
html=(root/'index.html').read_text()
js=(root/'src/room.js').read_text()
errors=[]
class Audit(HTMLParser):
 def __init__(self):
  super().__init__();self.ids=[];self.scripts=[];self.links=[]
 def handle_starttag(self,t,a):
  d=dict(a)
  if d.get('id'):self.ids.append(d['id'])
  if t=='script' and d.get('src'):self.scripts.append(d['src'])
  if t=='link' and d.get('href') and d.get('rel')=='stylesheet':self.links.append(d['href'])
p=Audit();p.feed(html)
if len(set(p.ids))!=len(p.ids):errors.append('duplicate IDs')
for f in p.scripts+p.links:
 if f.startswith('./') and not (root/f[2:]).exists():errors.append('missing asset '+f)
for x in ['world','preloader','bottom-nav','door-prompt','intake','screen-modal','canvas-modal','prop-modal','light-mode']:
 if x not in p.ids:errors.append('required element missing: '+x)
if 'loadModule' not in js:errors.append('resilient Three.js loader missing')
if not (root/'favicon.svg').is_file():errors.append('favicon missing')
for required in ['buildRoom','buildDesk','buildPiano','buildShelf','buildLighting','setView','knock','openIntake','openScreen','openCanvas']:
 if required not in js:errors.append('runtime function missing: '+required)
if errors:print('FAILED\\n'+'\\n'.join(errors));sys.exit(1)
print('Immersive studio validation passed.')
