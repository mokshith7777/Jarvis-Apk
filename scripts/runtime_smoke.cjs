#!/usr/bin/env node
'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const path=require('node:path');
const packageRoot=process.argv[3];
const mode=process.argv[4]||'full';
const target=process.argv[2];
if(!target||!packageRoot)throw new Error('Usage: runtime_smoke.cjs URL PLAYWRIGHT_NODE_MODULES [full|live]');
const {chromium}=require(path.join(packageRoot,'playwright'));
const browserArgs=['--enable-webgl','--ignore-gpu-blocklist','--use-angle=swiftshader','--disable-gpu-sandbox','--enable-unsafe-swiftshader'];
async function installPinnedDependencyRoute(page){
 if(mode!=='full')return;
 const packages={three:'three@0.170.0',gsap:'gsap@3.12.7',lenis:'lenis@1.1.16'};
 const dependencyFailures=[];
 await page.route('https://cdn.jsdelivr.net/npm/**',async route=>{
  const pathname=new URL(route.request().url()).pathname;
  let relative=null,packageName=null;
  if(pathname.startsWith('/npm/three@0.170.0/')){packageName='three';relative=pathname.slice('/npm/three@0.170.0/'.length);}
  else if(pathname.startsWith('/npm/gsap@3.12.7/')){packageName='gsap';relative=pathname.slice('/npm/gsap@3.12.7/'.length);if(relative==='+esm')relative='index.js';else if(relative==='ScrollTrigger/+esm')relative='ScrollTrigger.js';else if(relative==='ScrollTrigger/Observer.js')relative='Observer.js';}
  else if(pathname.startsWith('/npm/lenis@1.1.16/')){packageName='lenis';relative=pathname.slice('/npm/lenis@1.1.16/'.length);if(relative==='+esm')relative='dist/lenis.mjs';}
  if(!packageName||!relative){dependencyFailures.push('unmapped '+pathname);return route.abort();}
  const file=path.resolve(packageRoot,packageName,relative);
  const base=path.resolve(packageRoot,packageName)+path.sep;
  if(!file.startsWith(base)||!fs.existsSync(file)){dependencyFailures.push('missing '+packages[packageName]+'/'+relative);return route.abort();}
  await route.fulfill({status:200,contentType:'text/javascript; charset=utf-8',headers:{'access-control-allow-origin':'*'},body:fs.readFileSync(file)});
 });
 return dependencyFailures;
}
(async()=>{
const browser=await chromium.launch({headless:true,args:browserArgs});
async function revealBeforeInteraction(page,locator,label){
 await locator.evaluate(e=>e.scrollIntoView({block:'center',behavior:'instant'}));
 const reveal=await locator.evaluateHandle(e=>e.closest('.reveal')||e);
 await page.waitForFunction(el=>{const cs=getComputedStyle(el);return cs.visibility==='visible'&&Number(cs.opacity)>.95},reveal,{timeout:10000}).catch(async error=>{const details=await reveal.evaluate(el=>({tag:el.tagName,className:el.className,visibility:getComputedStyle(el).visibility,opacity:getComputedStyle(el).opacity,rect:el.getBoundingClientRect().toJSON()}));throw new Error(label+' reveal did not become visible: '+JSON.stringify(details)+'; '+error.message)});
}
async function openAndVerify(page,label){
 const pageErrors=[],failedModules=[],localDependencyFailures=await installPinnedDependencyRoute(page)||[];
 page.on('pageerror',e=>pageErrors.push(e.message));
 page.on('requestfailed',r=>{if(/three|gsap|lenis|main\.js|armor\.js/i.test(r.url()))failedModules.push(r.url()+': '+r.failure()?.errorText)});
 await page.goto(target,{waitUntil:'commit',timeout:30000});
 await page.waitForFunction(()=>{const r=window.__JARVIS_RUNTIME__;return r?.status==='active'||(r?.status==='fallback'&&r.reason!=='module-load-timeout')},null,{timeout:60000}).catch(()=>{});
 const runtime=await page.evaluate(()=>window.__JARVIS_RUNTIME__||null);
 assert(runtime&&runtime.status==='active',label+' runtime did not execute Three.js: '+JSON.stringify(runtime)+' pageErrors='+pageErrors.join(' | ')+' moduleRequests='+failedModules.join(' | ')+' localDependencies='+localDependencyFailures.join(' | '));
 assert.equal(runtime.renderer,'three',label+' renderer marker');
 assert(runtime.webglVersion>=1,label+' WebGL context version missing');
 assert.equal(runtime.canvasId,'cinematic-world',label+' renderer canvas marker');
 const legacyField=page.locator('.cinematic-techfield');assert.equal(await legacyField.evaluate(el=>getComputedStyle(el).display),'none',label+' obsolete abstract techfield must remain hidden');const abstractGrid=page.locator('.atmo-grid');if(await abstractGrid.count())assert.equal(await abstractGrid.first().evaluate(el=>getComputedStyle(el).display),'none',label+' abstract grid overlay must remain hidden');
 await page.waitForFunction(()=>window.__JARVIS_RUNTIME__?.frames>=3,null,{timeout:15000});
 assert.equal(pageErrors.length,0,label+' uncaught browser exceptions: '+pageErrors.join(' | '));
 return runtime;
}
try{
 const desktop=await browser.newPage({viewport:{width:1440,height:900}});
 const runtime=await openAndVerify(desktop,'desktop');
 const canvas=desktop.locator('#cinematic-world');
 assert(await canvas.isVisible(),'Three.js canvas not visible');
 const contextInfo=await desktop.evaluate(()=>{const gl=document.querySelector('#cinematic-world').getContext('webgl2')||document.querySelector('#cinematic-world').getContext('webgl');return {ok:!!gl,extension:gl?.getExtension('WEBGL_lose_context')||null}});
 assert(contextInfo.ok,'canvas context unavailable after runtime initialization');
 if(mode==='full'){
  assert(contextInfo.extension,'WEBGL_lose_context extension unavailable in CI browser');
  await desktop.evaluate(()=>{window.__smokeContext=document.querySelector('#cinematic-world').getContext('webgl2').getExtension('WEBGL_lose_context');window.__smokeContext.loseContext()});
  await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.status==='context-lost',null,{timeout:10000});
  assert.equal(await desktop.locator('#fallback').isVisible(),true,'fallback must become visible on context loss');
  const framesAtLoss=await desktop.evaluate(()=>window.__JARVIS_RUNTIME__?.frames||0);
  await desktop.evaluate(()=>window.__smokeContext.restoreContext());
  await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.status==='active'&&window.__JARVIS_RUNTIME__?.contextRestorations>=1,null,{timeout:20000});
  assert.equal(await desktop.locator('#fallback').isVisible(),false,'fallback must hide after successful context restoration');
  await desktop.waitForFunction(frames=>window.__JARVIS_RUNTIME__?.frames>frames,framesAtLoss,{timeout:15000});
  assert((await desktop.evaluate(()=>window.__JARVIS_RUNTIME__?.frames||0))>framesAtLoss,'render frames must resume after WebGL context restoration');
  const demo=desktop.locator('.demo-tab[data-demo="providers"]');await revealBeforeInteraction(desktop,demo,'provider demo tab');await demo.click();
  assert(await demo.evaluate(e=>e.classList.contains('active')),'demo tab should become active');
  assert((await desktop.locator('#demoscreen').innerText()).includes('Choose the engine.'),'demo tab content should update');
  const faq=desktop.locator('.faqitem button').first();await revealBeforeInteraction(desktop,faq,'FAQ button');await faq.click();
  assert(await faq.evaluate(e=>e.parentElement.classList.contains('active')),'FAQ should expand');
  const expand=desktop.locator('.expand').first();await revealBeforeInteraction(desktop,expand,'feature expand button');await expand.click();
  assert(await expand.evaluate(e=>e.closest('.feature').classList.contains('open')),'feature card should expand');
  const hosted=desktop.locator('.chip[data-provider="Hosted AI"]');await revealBeforeInteraction(desktop,hosted,'provider chip');await hosted.click();const dropzone=desktop.locator('#dropzone');await revealBeforeInteraction(desktop,dropzone,'dropzone');await dropzone.click();
  assert((await desktop.locator('#dropstatus').innerText()).includes('HOSTED AI'),'sandbox provider tap should connect demo');
  const deviceFamily=desktop.locator('#devicefamily');await revealBeforeInteraction(desktop,deviceFamily,'device selector');await desktop.waitForFunction(()=>{const e=document.querySelector('#devicefamily'),p=e?.closest('.reveal'),cs=p&&getComputedStyle(p);return e&&cs&&cs.visibility==='visible'&&Number(cs.opacity)>.95},null,{timeout:10000});await deviceFamily.selectOption('realme');
  assert((await desktop.locator('#device-tip').innerText()).includes('Realme'),'device selector should update help');
  const checklist=desktop.locator('.step-done').first();await revealBeforeInteraction(desktop,checklist,'setup checklist');await checklist.check();
  assert(await checklist.isChecked(),'setup checklist should accept a check');
  const reset=desktop.locator('#setup-reset');await revealBeforeInteraction(desktop,reset,'setup reset');await reset.click();assert.equal(await checklist.isChecked(),false,'setup reset should clear checklist');
  const audio=desktop.locator('#audio-toggle');assert.equal(await audio.getAttribute('aria-pressed'),'false','ambient audio must start off');
  await audio.click();assert.equal(await audio.getAttribute('aria-pressed'),'true','audio toggle should turn on');await audio.click();assert.equal(await audio.getAttribute('aria-pressed'),'false','audio toggle should turn off');
  const sound=desktop.locator('#sound-toggle');assert.equal(await sound.getAttribute('aria-pressed'),'false','sound effects must start off');
  await sound.click();assert.equal(await sound.getAttribute('aria-pressed'),'true','sound toggle should enable');await sound.click();assert.equal(await sound.getAttribute('aria-pressed'),'false','sound toggle should disable');
  await desktop.mouse.move(420,360);await desktop.waitForTimeout(150);
  const pointerX=await desktop.evaluate(()=>getComputedStyle(document.body).getPropertyValue('--pointer-x'));
  assert(pointerX.trim(),'hero pointer parallax should respond to pointer movement');
  const pricingAnchor=desktop.locator('a[href="#pricing"]').first();await revealBeforeInteraction(desktop,pricingAnchor,'pricing CTA');await pricingAnchor.click();assert.equal(new URL(desktop.url()).hash,'#pricing','hero CTA should navigate to pricing anchor');
  await desktop.locator('#story').scrollIntoViewIfNeeded();await desktop.waitForTimeout(600);const initialScroll=await desktop.evaluate(()=>window.__JARVIS_RUNTIME__?.scrollProgress||0);await desktop.mouse.wheel(0,650);await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.scrollProgress>.05,null,{timeout:10000});const firstScroll=await desktop.evaluate(()=>window.__JARVIS_RUNTIME__.scrollProgress);await desktop.mouse.wheel(0,900);await desktop.waitForFunction(p=>window.__JARVIS_RUNTIME__?.scrollProgress>p+.025,firstScroll,{timeout:10000});const secondScroll=await desktop.evaluate(()=>window.__JARVIS_RUNTIME__.scrollProgress);assert(secondScroll>firstScroll,'full-page cinematic progress should increase smoothly as the page scrolls');await desktop.mouse.wheel(0,-1100);await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.scrollProgress<.9,null,{timeout:10000});assert(initialScroll>=0,'scroll telemetry should be initialized');const firstReveal=desktop.locator('#features .feature.reveal').first();await firstReveal.scrollIntoViewIfNeeded();await desktop.waitForFunction(()=>{const e=document.querySelector('#features .feature.reveal'),cs=e&&getComputedStyle(e);return cs&&cs.visibility==='visible'&&Number(cs.opacity)>.95},null,{timeout:10000});await desktop.mouse.wheel(0,-6000);await desktop.waitForFunction(()=>window.scrollY<100,null,{timeout:10000});await desktop.waitForFunction(()=>{const e=document.querySelector('#features .feature.reveal'),cs=e&&getComputedStyle(e);return cs&&cs.visibility==='hidden'&&Number(cs.opacity)<.1},null,{timeout:10000});
 }
 const mobileContext=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,reducedMotion:'reduce'});
 const mobile=await mobileContext.newPage();const mobileRuntime=await openAndVerify(mobile,'mobile reduced-motion');
 assert.equal(mobileRuntime.mobile,true,'mobile optimization should be active');
 assert.equal(mobileRuntime.reducedMotion,true,'reduced-motion preference should be respected');
 assert.equal(await mobile.locator('#audio-toggle').getAttribute('aria-pressed'),'false','mobile audio must default off');
 if(mode==='full'){const localChip=mobile.locator('.chip[data-provider="Local endpoint"]');await revealBeforeInteraction(mobile,localChip,'mobile local provider chip');await localChip.tap();const mobileDrop=mobile.locator('#dropzone');await revealBeforeInteraction(mobile,mobileDrop,'mobile dropzone');await mobileDrop.tap();assert((await mobile.locator('#dropstatus').innerText()).includes('LOCAL ENDPOINT'),'mobile tap sandbox interaction should work')}
 await mobileContext.close();await desktop.close();
 console.log('BROWSER RUNTIME SMOKE PASSED: Three.js executed, render frames observed, '+(mode==='full'?'WebGL context recovery and desktop/mobile interactions verified.':'live runtime execution verified.'));
 console.log('Visual acceptance is NOT assessed by this automated browser smoke; realistic cinematic media still requires human review.');
}catch(error){console.error('BROWSER RUNTIME SMOKE FAILED:',error.stack||error);process.exitCode=1}
finally{await browser.close()}
})().catch(error=>{console.error('BROWSER SMOKE SETUP FAILED:',error.stack||error);process.exitCode=1});
