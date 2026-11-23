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
 const state=await locator.evaluate(el=>new Promise(resolve=>{
  const target=el.closest('.reveal')||el,deadline=performance.now()+10000;
  function inspect(){
   const revealStyle=getComputedStyle(target),elementStyle=getComputedStyle(el),revealRect=target.getBoundingClientRect(),elementRect=el.getBoundingClientRect();
   const ancestors=[];for(let node=el;node&&node!==document.body;node=node.parentElement){const s=getComputedStyle(node);ancestors.push({tag:node.tagName,id:node.id,className:node.className,visibility:s.visibility,opacity:s.opacity,inlineStyle:node.getAttribute('style')});}const details={tag:target.tagName,className:target.className,visibility:revealStyle.visibility,opacity:revealStyle.opacity,elementVisibility:elementStyle.visibility,elementOpacity:elementStyle.opacity,revealRect:revealRect.toJSON(),elementRect:elementRect.toJSON(),ancestors};
   if(revealStyle.visibility==='visible'&&Number(revealStyle.opacity)>.95&&elementStyle.visibility==='visible'&&elementRect.width>0&&elementRect.height>0){resolve({ready:true,details});return;}
   if(performance.now()>=deadline){resolve({ready:false,details});return;}
   requestAnimationFrame(inspect);
  }
  inspect();
 }));
 if(!state.ready)throw new Error(label+' reveal did not become visible: '+JSON.stringify(state.details));
}
async function waitForImageLoaded(page,selector,label){
 try{await page.waitForFunction(sel=>{const image=document.querySelector(sel);return image&&image.complete&&image.naturalWidth>0},selector,{timeout:20000})}catch(error){const state=await page.evaluate(sel=>{const image=document.querySelector(sel);return {exists:!!image,complete:image?.complete,naturalWidth:image?.naturalWidth,naturalHeight:image?.naturalHeight,currentSrc:image?.currentSrc}},selector);throw new Error(label+' image failed to load: '+JSON.stringify(state)+'; '+error.message)}
}
async function waitForLoaderHidden(page,label){
 try{
  await page.waitForFunction(()=>{const e=document.querySelector('#loading');return e&&getComputedStyle(e).visibility==='hidden'},null,{timeout:15000});
 }catch(error){
  const state=await page.evaluate(()=>{const e=document.querySelector('#loading'),s=e&&getComputedStyle(e);return {exists:!!e,className:e?.className,visibility:s?.visibility,opacity:s?.opacity,display:s?.display,readyState:document.readyState,runtime:window.__JARVIS_RUNTIME__,bodyClasses:document.body.className}});
  let screenshot='';try{screenshot=(await page.screenshot({type:'jpeg',quality:35})).toString('base64')}catch(captureError){screenshot='capture failed: '+String(captureError)}
  console.error('LOADER_DIAGNOSTICS_'+label+':'+JSON.stringify(state));
  console.error('LOADER_FAILURE_SCREENSHOT_'+label+':'+screenshot);
  throw new Error(label+' loader remained visible: '+JSON.stringify(state)+'; '+error.message);
 }
}
async function waitForScrollReset(page,label){
 try{
  await page.waitForFunction(()=>window.scrollY<5&&window.__JARVIS_RUNTIME__?.scrollTarget<.02,null,{timeout:10000});
  await page.waitForFunction(()=>window.__JARVIS_RUNTIME__?.scrollProgress<.02,null,{timeout:15000});
 }catch(error){
  const state=await page.evaluate(()=>({scrollY:window.scrollY,scrollMax:Math.max(0,document.documentElement.scrollHeight-innerHeight),scrollProgress:window.__JARVIS_RUNTIME__?.scrollProgress,scrollTarget:window.__JARVIS_RUNTIME__?.scrollTarget,status:window.__JARVIS_RUNTIME__?.status,frames:window.__JARVIS_RUNTIME__?.frames,lastRawDelta:window.__JARVIS_RUNTIME__?.lastRawDelta,documentHidden:window.__JARVIS_RUNTIME__?.documentHidden}));
  throw new Error(label+' scroll target/progress failed to reset: '+JSON.stringify(state)+'; '+error.message);
 }
}
async function openAndVerify(page,label){
 const pageErrors=[],failedModules=[],failedVisualRequests=[],localDependencyFailures=await installPinnedDependencyRoute(page)||[];
 page.on('pageerror',e=>pageErrors.push(e.message));
 page.on('requestfailed',r=>{if(/three|gsap|lenis|main\.js|armor\.js/i.test(r.url()))failedModules.push(r.url()+': '+r.failure()?.errorText);if(/unsplash|hdqwalls/i.test(r.url()))failedVisualRequests.push(r.url()+': '+r.failure()?.errorText)});
 await page.goto(target,{waitUntil:'commit',timeout:30000});
 await page.waitForFunction(()=>{const r=window.__JARVIS_RUNTIME__;return r?.status==='active'||(r?.status==='fallback'&&r.reason!=='module-load-timeout')},null,{timeout:60000}).catch(()=>{});
 const runtime=await page.evaluate(()=>window.__JARVIS_RUNTIME__||null);
 assert(runtime&&runtime.status==='active',label+' runtime did not execute Three.js: '+JSON.stringify(runtime)+' pageErrors='+pageErrors.join(' | ')+' moduleRequests='+failedModules.join(' | ')+' localDependencies='+localDependencyFailures.join(' | '));
 assert.equal(runtime.renderer,'three',label+' renderer marker');
 assert(runtime.webglVersion>=1,label+' WebGL context version missing');
 assert.equal(runtime.canvasId,'cinematic-world',label+' renderer canvas marker');
  assert.equal(await page.locator('#nature-backdrop').count(),1,label+' layered nature backdrop missing');assert.equal(await page.locator('#nature-backdrop svg').count(),1,label+' nature SVG missing');assert.equal(await page.locator('#nature-backdrop').evaluate(el=>getComputedStyle(el).zIndex),'0',label+' nature backdrop must remain behind the scene');assert.equal(await page.locator('#cinematic-world').evaluate(el=>getComputedStyle(el).zIndex),'1',label+' transparent scene canvas must sit above the nature backdrop');await waitForImageLoaded(page,'#nature-photo',label+' nature photo must load');await waitForImageLoaded(page,'#armor-image',label+' armor image must load');const armorStage=page.locator('.hero.cinematic-hero .armor-stage');assert.equal(await armorStage.evaluate(el=>getComputedStyle(el).display),'grid',label+' armor reference stage must be displayed');assert(await armorStage.isVisible(),label+' armor stage must be visible');
 assert.equal(await page.locator('.cinematic-techfield').count(),0,label+' legacy techfield markup must be removed');const abstractGrid=page.locator('.atmo-grid');if(await abstractGrid.count())assert.equal(await abstractGrid.first().evaluate(el=>getComputedStyle(el).display),'none',label+' abstract grid overlay must remain hidden');
 await page.waitForFunction(()=>window.__JARVIS_RUNTIME__?.frames>=3,null,{timeout:15000});
 if(label.includes('mobile')){const layout=await page.evaluate(()=>{const copy=document.querySelector('.cinematic-hero .copy')?.getBoundingClientRect(),stage=document.querySelector('.cinematic-hero .armor-stage')?.getBoundingClientRect();return {copy:copy?.toJSON(),stage:stage?.toJSON(),viewport:{width:innerWidth,height:innerHeight}}});assert(layout.copy&&layout.stage&&layout.copy.bottom<=layout.stage.top+4,'mobile hero copy must not overlap armor stage: '+JSON.stringify(layout));assert(layout.stage.bottom<=layout.viewport.height+8,'mobile armor stage must fit the initial viewport: '+JSON.stringify(layout));}
 assert.equal(pageErrors.length,0,label+' uncaught browser exceptions: '+pageErrors.join(' | '));
 return runtime;
}
try{
 const desktop=await browser.newPage({viewport:{width:1440,height:900}});
 const runtime=await openAndVerify(desktop,'desktop');await waitForLoaderHidden(desktop,'desktop');console.log('DESKTOP_RENDER_DIAGNOSTICS:'+JSON.stringify(runtime));const desktopVisual=await desktop.screenshot({type:'jpeg',quality:42});console.log('VISUAL_AUDIT_DESKTOP_JPEG_BASE64:'+desktopVisual.toString('base64'));
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
  const secondStep=desktop.locator('.setup-step[data-step="1"] .step-toggle');await revealBeforeInteraction(desktop,secondStep,'setup step accordion');await secondStep.click();assert.equal(await secondStep.getAttribute('aria-expanded'),'true','setup step should expand');assert(await desktop.locator('.setup-step[data-step="1"] .step-body').isVisible(),'expanded setup step body should be visible');
  const firstStep=desktop.locator('.setup-step[data-step="0"] .step-toggle');await revealBeforeInteraction(desktop,firstStep,'setup first step accordion');await firstStep.click();assert.equal(await firstStep.getAttribute('aria-expanded'),'true','first setup step should reopen');
  const checklist=desktop.locator('.step-done').first();await revealBeforeInteraction(desktop,checklist,'setup checklist');await checklist.check();
  assert(await checklist.isChecked(),'setup checklist should accept a check');assert.equal(await desktop.locator('#setup-progress-text').innerText(),'1 / 8 COMPLETE','setup progress should update after checking');assert.equal(await desktop.locator('.progress-track').getAttribute('aria-valuenow'),'1','progressbar value should update');
  const reset=desktop.locator('#setup-reset');await revealBeforeInteraction(desktop,reset,'setup reset');await reset.click();assert.equal(await checklist.isChecked(),false,'setup reset should clear checklist');assert.equal(await desktop.locator('#setup-progress-text').innerText(),'0 / 8 COMPLETE','setup reset should clear progress');assert.equal(await desktop.locator('.progress-track').getAttribute('aria-valuenow'),'0','progressbar should reset');
  const audio=desktop.locator('#audio-toggle');assert.equal(await audio.getAttribute('aria-pressed'),'false','ambient audio must start off');
  await audio.click();assert.equal(await audio.getAttribute('aria-pressed'),'true','audio toggle should turn on');await audio.click();assert.equal(await audio.getAttribute('aria-pressed'),'false','audio toggle should turn off');
  const sound=desktop.locator('#sound-toggle');assert.equal(await sound.getAttribute('aria-pressed'),'false','sound effects must start off');
  await sound.click();assert.equal(await sound.getAttribute('aria-pressed'),'true','sound toggle should enable');await sound.click();assert.equal(await sound.getAttribute('aria-pressed'),'false','sound toggle should disable');
  // Pointer parallax intentionally fades out as the camera approaches the armor. Test it in the hero, not at the setup section reached by prior reveal interactions.
  await desktop.evaluate(()=>window.scrollTo({top:Math.round((document.documentElement.scrollHeight-innerHeight)*.56),left:0,behavior:'instant'}));await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.scrollProgress>.55,null,{timeout:10000});await desktop.waitForFunction(()=>window.JARVIS3D?.armor?.visible===true,null,{timeout:10000});assert.equal(await desktop.evaluate(()=>window.JARVIS3D.armor.visible),true,'procedural armor must crossfade in for the fracture chapter');await desktop.evaluate(()=>window.scrollTo({top:0,left:0,behavior:'instant'}));await waitForScrollReset(desktop,'armor crossfade reverse to top');await desktop.waitForFunction(()=>window.JARVIS3D?.armor?.visible===false,null,{timeout:10000});assert.equal(await desktop.evaluate(()=>window.JARVIS3D.armor.visible),false,'photographic armor should be restored at hero top');
  const cameraAtNeutral=await desktop.evaluate(()=>window.JARVIS3D.camera.position.x);await desktop.mouse.move(1100,100);await desktop.waitForFunction(x=>Math.abs(window.JARVIS3D.camera.position.x-x)>.03,cameraAtNeutral,{timeout:15000});const cameraAtRight=await desktop.evaluate(()=>window.JARVIS3D.camera.position.x);await desktop.mouse.move(200,100);await desktop.waitForFunction(x=>Math.abs(window.JARVIS3D.camera.position.x-x)>.03,cameraAtRight,{timeout:15000});const cameraAtLeft=await desktop.evaluate(()=>window.JARVIS3D.camera.position.x);assert(Math.abs(cameraAtLeft-cameraAtRight)>.03,'3D camera parallax should respond to pointer movement in the hero');
  const pricingAnchor=desktop.locator('a[href="#pricing"]').first();await revealBeforeInteraction(desktop,pricingAnchor,'pricing CTA');await pricingAnchor.click();assert.equal(new URL(desktop.url()).hash,'#pricing','hero CTA should navigate to pricing anchor');
  await desktop.locator('#story').scrollIntoViewIfNeeded();await desktop.waitForTimeout(600);const initialScroll=await desktop.evaluate(()=>window.__JARVIS_RUNTIME__?.scrollProgress||0);await desktop.mouse.wheel(0,650);await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.scrollProgress>.05,null,{timeout:10000});const firstScroll=await desktop.evaluate(()=>window.__JARVIS_RUNTIME__.scrollProgress);await desktop.mouse.wheel(0,900);await desktop.waitForFunction(p=>window.__JARVIS_RUNTIME__?.scrollProgress>p+.025,firstScroll,{timeout:10000});const secondScroll=await desktop.evaluate(()=>window.__JARVIS_RUNTIME__.scrollProgress);assert(secondScroll>firstScroll,'full-page cinematic progress should increase smoothly as the page scrolls');await desktop.mouse.wheel(0,-1100);await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.scrollProgress<.9,null,{timeout:10000});assert(initialScroll>=0,'scroll telemetry should be initialized');const firstReveal=desktop.locator('#features .feature.reveal').first();await firstReveal.scrollIntoViewIfNeeded();await desktop.waitForFunction(()=>{const e=document.querySelector('#features .feature.reveal'),cs=e&&getComputedStyle(e);return cs&&cs.visibility==='visible'&&Number(cs.opacity)>.95},null,{timeout:10000});await desktop.mouse.wheel(0,-6000);await waitForScrollReset(desktop,'reverse-scroll to top');assert((await desktop.evaluate(()=>window.__JARVIS_RUNTIME__.scrollProgress))<.02,'full-page progress should return near zero at page top after reverse scroll');await desktop.waitForFunction(()=>{const e=document.querySelector('#features .feature.reveal'),cs=e&&getComputedStyle(e);return cs&&cs.visibility==='hidden'&&Number(cs.opacity)<.1},null,{timeout:10000});await firstReveal.scrollIntoViewIfNeeded();await desktop.waitForFunction(()=>{const e=document.querySelector('#features .feature.reveal'),cs=e&&getComputedStyle(e);return cs&&cs.visibility==='visible'&&Number(cs.opacity)>.95},null,{timeout:10000});assert(await firstReveal.isVisible(),'reveal should re-enter visibly after scrolling down again');await desktop.mouse.wheel(0,-8000);await waitForScrollReset(desktop,'second reverse-scroll to top');await desktop.waitForFunction(()=>{const e=document.querySelector('#features .feature.reveal'),cs=e&&getComputedStyle(e);return cs&&cs.visibility==='hidden'&&Number(cs.opacity)<.1},null,{timeout:10000});
 }
 const mobileContext=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,reducedMotion:'reduce'});
 const mobile=await mobileContext.newPage();const mobileRuntime=await openAndVerify(mobile,'mobile reduced-motion');await waitForLoaderHidden(mobile,'mobile');console.log('MOBILE_RENDER_DIAGNOSTICS:'+JSON.stringify(mobileRuntime));const mobileVisual=await mobile.screenshot({type:'jpeg',quality:42});console.log('VISUAL_AUDIT_MOBILE_JPEG_BASE64:'+mobileVisual.toString('base64'));
 assert.equal(mobileRuntime.mobile,true,'mobile optimization should be active');
 assert.equal(mobileRuntime.reducedMotion,true,'reduced-motion preference should be respected');
  assert.equal(await mobile.locator('#nature-backdrop .cloud-drift').evaluateAll(els=>els.every(el=>getComputedStyle(el).animationName==='none')),true,'nature cloud animation must stop for reduced motion on every cloud layer');assert.equal(await mobile.locator('#nature-backdrop .parallax-far').evaluate(el=>getComputedStyle(el).translate),'none','nature parallax must stop for reduced motion');
 assert.equal(await mobile.locator('#audio-toggle').getAttribute('aria-pressed'),'false','mobile audio must default off');assert.equal(await mobile.locator('#sound-toggle').evaluate(el=>getComputedStyle(el).display),'inline-flex','mobile sound toggle must use the styled control layout');assert.equal(await mobile.locator('#nature-photo').evaluate(el=>getComputedStyle(el).translate),'none','nature photo parallax must stop for reduced motion');assert.equal(await mobile.evaluate(()=>window.JARVIS3D.armor.visible),false,'reduced-motion hero should retain the static armor reference');
 if(mode==='full'){const localChip=mobile.locator('.chip[data-provider="Local endpoint"]');await revealBeforeInteraction(mobile,localChip,'mobile local provider chip');await localChip.tap();const mobileDrop=mobile.locator('#dropzone');await revealBeforeInteraction(mobile,mobileDrop,'mobile dropzone');await mobileDrop.tap();assert((await mobile.locator('#dropstatus').innerText()).includes('LOCAL ENDPOINT'),'mobile tap sandbox interaction should work')}
 await mobileContext.close();await desktop.close();
 console.log('BROWSER RUNTIME SMOKE PASSED: Three.js executed, render frames observed, '+(mode==='full'?'WebGL context recovery and desktop/mobile interactions verified.':'live runtime execution verified.'));
 console.log('Visual acceptance is NOT assessed by this automated browser smoke; realistic cinematic media still requires human review.');
}catch(error){console.error('BROWSER RUNTIME SMOKE FAILED:',error.stack||error);process.exitCode=1}
finally{await browser.close()}
})().catch(error=>{console.error('BROWSER SMOKE SETUP FAILED:',error.stack||error);process.exitCode=1});
