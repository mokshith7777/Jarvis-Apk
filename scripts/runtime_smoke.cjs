const { chromium } = require('playwright');
const assert = require('node:assert/strict');
(async()=>{
 let browser;
 try { browser=await chromium.launch({headless:true,channel:'chrome'}); }
 catch { browser=await chromium.launch({headless:true}); }
 const page=await browser.newPage({viewport:{width:1440,height:900},reducedMotion:'reduce'});
 const errors=[];
 page.on('pageerror',e=>errors.push(e.message));
 page.on('console',m=>{if(m.type()==='error')errors.push(m.text());});
 page.on('response',r=>{if(r.status()>=400)errors.push('HTTP '+r.status()+' '+r.url());});
 await page.goto(process.env.SITE_URL||'http://127.0.0.1:4173',{waitUntil:'domcontentloaded',timeout:60000});
 await page.waitForTimeout(3000);
 assert.equal(await page.title(),'JARVIS PRO · Enter the Room');
 assert.equal(await page.locator('h1').count(),1);
 assert.equal(await page.locator('#room-world').count(),1);
 assert.equal(await page.locator('#features').count(),1);
 await page.locator('a[href="#features"]').first().click();
 await page.waitForTimeout(350);
 assert.equal(new URL(page.url()).hash,'#features');
 await page.locator('#enter-room').click();
 assert.ok(await page.locator('body').evaluate(b=>b.classList.contains('entered')));
 await page.locator('#pricing').scrollIntoViewIfNeeded();
 assert.ok(await page.locator('#pricing').isVisible());
 await page.locator('#faq details').first().locator('summary').click();
 assert.ok(await page.locator('#faq details').first().evaluate(e=>e.open));
 await page.screenshot({path:'/tmp/jarvis-room-desktop.png'});
 await page.setViewportSize({width:390,height:844});
 await page.locator('#enter-room').isVisible();
 await page.locator('#pricing').scrollIntoViewIfNeeded();
 assert.ok(await page.locator('#pricing').isVisible());
 await page.screenshot({path:'/tmp/jarvis-room-mobile.png'});
 if(errors.length)throw new Error('Browser console/page errors: '+errors.join('\n'));
 await browser.close();
 console.log('ROOM WEBSITE SMOKE PASSED: desktop/mobile, navigation, entry and FAQ.');
})().catch(e=>{console.error(e);process.exit(1);});
