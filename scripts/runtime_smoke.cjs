const {chromium}=require('playwright');const assert=require('node:assert/strict');
(async()=>{let b;try{b=await chromium.launch({headless:true,channel:'chrome'})}catch{b=await chromium.launch({headless:true})}
const p=await b.newPage({viewport:{width:1280,height:800}}),errs=[];p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>{if(m.type()==='error')errs.push(m.text())});p.on('response',r=>{if(r.status()>=400)errs.push('HTTP '+r.status()+': '+r.url())});
await p.goto(process.env.SITE_URL||'http://127.0.0.1:4173',{waitUntil:'domcontentloaded'});await p.waitForTimeout(2500);
assert.equal(await p.locator('#world').count(),1);assert.equal(await p.locator('#bottom-nav').count(),1);assert.equal(await p.locator('#content-panel').count(),1);assert.ok(await p.locator('body').evaluate(e=>getComputedStyle(e).overflow==='hidden'));
await p.locator('[data-view="desk"]').click();await p.setViewportSize({width:390,height:844});assert.ok(await p.locator('#world').isVisible());if(errs.length)throw new Error(errs.join('\\n'));await b.close();console.log('Immersive room smoke passed.');})().catch(e=>{console.error(e);process.exit(1)});
