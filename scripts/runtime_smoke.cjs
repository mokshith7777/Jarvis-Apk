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
async function openAndVerify(page,label){
 const pageErrors=[],failedModules=[],localDependencyFailures=await installPinnedDependencyRoute(page)||[];
 page.on('pageerror',e=>pageErrors.push(e.message));
 page.on('requestfailed',r=>{if(/three|gsap|lenis|main\.js|armor\.js/i.test(r.url()))failedModules.push(r.url()+': '+r.failure()?.errorText)});
 await page.goto(target,{waitUntil:'commit',timeout:30000});
 await page.waitForFunction(()=>['active','fallback'].includes(window.__JARVIS_RUNTIME__?.status),null,{timeout:45000}).catch(()=>{});
 const runtime=await page.evaluate(()=>window.__JARVIS_RUNTIME__||null);
 assert(runtime&&runtime.status==='active',label+' runtime did not execute Three.js: '+JSON.stringify(runtime)+' pageErrors='+pageErrors.join(' | ')+' moduleRequests='+failedModules.join(' | ')+' localDependencies='+localDependencyFailures.join(' | '));
 assert.equal(runtime.renderer,'three',label+' renderer marker');
 assert(runtime.webglVersion>=1,label+' WebGL context version missing');
 assert.equal(runtime.canvasId,'cinematic-world',label+' renderer canvas marker');
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
  await desktop.evaluate(()=>window.__smokeContext.restoreContext());
  await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.status==='active'&&window.__JARVIS_RUNTIME__?.contextRestorations>=1,null,{timeout:20000});
  assert.equal(await desktop.locator('#fallback').isVisible(),false,'fallback must hide after successful context restoration');
  await desktop.waitForFunction(()=>window.__JARVIS_RUNTIME__?.frames>=3,null,{timeout:15000});
  const demo=desktop.locator('.demo-tab[data-demo="providers"]');await demo.click();
  assert(await demo.evaluate(e=>e.classList.contains('active')),'demo tab should become active');
  assert((await desktop.locator('#demoscreen').innerText()).includes('Choose the engine.'),'demo tab content should update');
  const faq=desktop.locator('.faqitem button').first();await faq.click();
  assert(await faq.evaluate(e=>e.parentElement.classList.contains('active')),'FAQ should expand');
  const expand=desktop.locator('.expand').first();await expand.click();
  assert(await expand.evaluate(e=>e.closest('.feature').classList.contains('open')),'feature card should expand');
  await desktop.locator('.chip[data-provider="Hosted AI"]').click();await desktop.locator('#dropzone').click();
  assert((await desktop.locator('#dropstatus').innerText()).includes('HOSTED AI'),'sandbox provider tap should connect demo');
  await desktop.locator('#devicefamily').selectOption('realme');
  assert((await desktop.locator('#device-tip').innerText()).includes('Realme'),'device selector should update help');
  const checklist=desktop.locator('.step-done').first();await checklist.check();
  assert(await checklist.isChecked(),'setup checklist should accept a check');
  await desktop.locator('#setup-reset').click();assert.equal(await checklist.isChecked(),false,'setup reset should clear checklist');
  const audio=desktop.locator('#audio-toggle');assert.equal(await audio.getAttribute('aria-pressed'),'false','ambient audio must start off');
  await audio.click();assert.equal(await audio.getAttribute('aria-pressed'),'true','audio toggle should turn on');await audio.click();assert.equal(await audio.getAttribute('aria-pressed'),'false','audio toggle should turn off');
  const sound=desktop.locator('#sound-toggle');assert.equal(await sound.getAttribute('aria-pressed'),'false','sound effects must start off');
  await sound.click();assert.equal(await sound.getAttribute('aria-pressed'),'true','sound toggle should enable');await sound.click();assert.equal(await sound.getAttribute('aria-pressed'),'false','sound toggle should disable');
  await desktop.mouse.move(420,360);await desktop.waitForTimeout(150);
  const pointerX=await desktop.evaluate(()=>getComputedStyle(document.body).getPropertyValue('--pointer-x'));
  assert(pointerX.trim(),'hero pointer parallax should respond to pointer movement');
  await desktop.locator('a[href="#pricing"]').first().click();assert.equal(new URL(desktop.url()).hash,'#pricing','hero CTA should navigate to pricing anchor');
  await desktop.locator('#story').scrollIntoViewIfNeeded();await desktop.waitForTimeout(600);
 }
 const mobileContext=await browser.newContext({viewport:{width:390,height:844},isMobile:true,hasTouch:true,reducedMotion:'reduce'});
 const mobile=await mobileContext.newPage();const mobileRuntime=await openAndVerify(mobile,'mobile reduced-motion');
 assert.equal(mobileRuntime.mobile,true,'mobile optimization should be active');
 assert.equal(mobileRuntime.reducedMotion,true,'reduced-motion preference should be respected');
 assert.equal(await mobile.locator('#audio-toggle').getAttribute('aria-pressed'),'false','mobile audio must default off');
 if(mode==='full'){await mobile.locator('.chip[data-provider="Local endpoint"]').tap();await mobile.locator('#dropzone').tap();assert((await mobile.locator('#dropstatus').innerText()).includes('LOCAL ENDPOINT'),'mobile tap sandbox interaction should work')}
 await mobileContext.close();await desktop.close();
 console.log('BROWSER RUNTIME SMOKE PASSED: Three.js executed, render frames observed, '+(mode==='full'?'WebGL context recovery and desktop/mobile interactions verified.':'live runtime execution verified.'));
 console.log('Visual acceptance is NOT assessed by this automated browser smoke; realistic cinematic media still requires human review.');
}catch(error){console.error('BROWSER RUNTIME SMOKE FAILED:',error.stack||error);process.exitCode=1}
finally{await browser.close()}
})().catch(error=>{console.error('BROWSER SMOKE SETUP FAILED:',error.stack||error);process.exitCode=1});
