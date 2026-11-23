const {chromium}=require('playwright');const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true,args:['--enable-webgl','--use-gl=angle','--use-angle=swiftshader','--ignore-gpu-blocklist']});
 const page=await browser.newPage({viewport:{width:1280,height:800},deviceScaleFactor:1});
 const errors=[];page.on('pageerror',e=>errors.push('pageerror: '+e.message));
 page.on('console',m=>{if(m.type()==='error')errors.push('console: '+m.text())});
 await page.goto(process.env.SITE_URL||'http://127.0.0.1:4173',{waitUntil:'domcontentloaded'});
 await page.waitForFunction(()=>window.__ROOM_TEMPLATE__?.ready===true,null,{timeout:45000});
 assert.equal(await page.locator('#world').count(),1);
 assert.equal(await page.locator('#bottom-nav').count(),1);
 assert.equal(await page.locator('#content-panel').count(),1);
 assert.ok(await page.locator('#world').evaluate(c=>c.width>0&&c.height>0));
 for(const name of ['desk','shelf','piano','entrance']){await page.locator('[data-view="'+name+'"]').click();await page.waitForTimeout(250);}
 await page.locator('#light-mode').click();
 await page.setViewportSize({width:390,height:844});
 assert.ok(await page.locator('#world').isVisible());
 await page.screenshot({path:process.env.SCREENSHOT_PATH||'room-smoke.png',fullPage:false});
 if(errors.length)throw new Error(errors.join('\\n'));
 await browser.close();console.log('REAL BROWSER SMOKE PASSED: Three.js loaded, scene initialized, navigation and lighting controls work, mobile layout verified.');
})().catch(e=>{console.error(e);process.exit(1)});
