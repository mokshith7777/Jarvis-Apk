import * as THREE from 'three';
const canvas=document.querySelector('#room-world'),loader=document.querySelector('#room-loader'),status=document.querySelector('#scene-status'),coords=document.querySelector('#scene-coordinates'),fallback=document.querySelector('#webgl-fallback');
const panel=document.querySelector('#object-panel'),panelTitle=document.querySelector('#panel-title'),panelText=document.querySelector('#panel-text'),panelLink=document.querySelector('#panel-link');
const mobile=matchMedia('(max-width:760px)').matches,reduced=matchMedia('(prefers-reduced-motion:reduce)').matches;
let renderer,scene,camera,clock,raycaster,mouse,core,coreLight,hotspots=[],lookX=0,lookY=0,lookTX=0,lookTY=0,chapter=0;
const chapters=['entry','features','setup','pricing','faq'];
const stops=[
{p:[0,1.72,8.5],t:[0,1.6,-3.1]},
{p:[0,1.72,3.8],t:[0,1.5,-3.5]},
{p:[0,1.72,1.8],t:[0,1.65,-4.8]},
{p:[2.35,1.62,-1],t:[-.1,1.45,-4.5]},
{p:[0,1.65,-3.5],t:[0,1.45,-5.3]}
];
const clamp=(v,a,b)=>Math.max(a,Math.min(b,v)),smooth=v=>v*v*(3-2*v);
const M=(c,metal=0,rough=.5,em=0,ei=0)=>new THREE.MeshStandardMaterial({color:c,metalness:metal,roughness:rough,emissive:em,emissiveIntensity:ei});
const black=M('#090e14',.7,.32),metal=M('#303a44',.82,.3),steel=M('#687884',.8,.25),cyan=M('#8cecff',.4,.2,0x28b8ed,2),redGlow=M('#ff3159',.35,.22,0x7d0b26,2),whiteGlow=M('#d9f8ff',.2,.15,0x6be5ff,1.2),glass=M('#091c2a',.5,.18,0x061b2c,.6);
function canvasTex(w,h,paint){const c=document.createElement('canvas');c.width=w;c.height=h;const g=c.getContext('2d');paint(g,w,h);const t=new THREE.CanvasTexture(c);t.colorSpace=THREE.SRGBColorSpace;t.anisotropy=8;return t;}
const floorTexture=canvasTex(1024,1024,(g,w,h)=>{g.fillStyle='#151b21';g.fillRect(0,0,w,h);for(let y=0;y<h;y+=128)for(let x=0;x<w;x+=128){g.fillStyle=((x+y)/128%2)?'#1b232b':'#121920';g.fillRect(x+2,y+2,124,124);g.strokeStyle='#394550';g.lineWidth=2;g.strokeRect(x+2,y+2,124,124);}});
floorTexture.wrapS=floorTexture.wrapT=THREE.RepeatWrapping;floorTexture.repeat.set(3,3);
const wallTexture=canvasTex(1024,512,(g,w,h)=>{g.fillStyle='#171e26';g.fillRect(0,0,w,h);for(let i=0;i<12;i++){const y=i*42;g.fillStyle=i%2?'#1b242d':'#151d25';g.fillRect(0,y,w,38);g.strokeStyle='#384651';g.lineWidth=2;g.strokeRect(2,y+2,w-4,34);for(let j=0;j<9;j++){g.fillStyle='#0b1118';g.fillRect(j*112+12,y+12,4,4);}}});
wallTexture.wrapS=THREE.RepeatWrapping;wallTexture.repeat.set(2,1);
const floorMat=new THREE.MeshStandardMaterial({map:floorTexture,metalness:.48,roughness:.65}),wallMat=new THREE.MeshStandardMaterial({map:wallTexture,metalness:.62,roughness:.5});
function box(parent,name,size,pos,material){const o=new THREE.Mesh(new THREE.BoxGeometry(...size),material);o.name=name;o.position.set(...pos);o.castShadow=true;o.receiveShadow=true;parent.add(o);return o;}
function cylinder(parent,name,a,b,h,pos,material,n=32){const o=new THREE.Mesh(new THREE.CylinderGeometry(a,b,h,n),material);o.name=name;o.position.set(...pos);o.castShadow=true;o.receiveShadow=true;parent.add(o);return o;}
function sphere(parent,name,r,pos,material,n=32){const o=new THREE.Mesh(new THREE.SphereGeometry(r,n,n/2),material);o.name=name;o.position.set(...pos);parent.add(o);return o;}
function tube(parent,name,points,r,material){const curve=new THREE.CatmullRomCurve3(points.map(p=>new THREE.Vector3(...p)));const o=new THREE.Mesh(new THREE.TubeGeometry(curve,36,r,8,false),material);o.name=name;parent.add(o);return o;}
function ring(parent,name,r,t,pos,material){const o=new THREE.Mesh(new THREE.TorusGeometry(r,t,16,90),material);o.name=name;o.position.set(...pos);parent.add(o);return o;}
function addHotspot(o,data){o.userData.hotspot=data;hotspots.push(o);return o;}
function screenTexture(title,color,sub){return canvasTex(768,432,(g,w,h)=>{g.fillStyle='#07111a';g.fillRect(0,0,w,h);g.fillStyle='#0b202b';g.fillRect(0,0,w,42);g.fillStyle=color;g.font='18px monospace';g.fillText('J / '+title.toUpperCase(),24,27);g.fillStyle='#8ba7b7';g.font='13px monospace';g.fillText(sub,24,74);g.strokeStyle=color+'77';g.lineWidth=2;g.strokeRect(24,96,w-48,h-120);for(let i=0;i<7;i++){g.fillStyle=color+'44';g.fillRect(45,120+i*34,(i*39%280)+160,5);}g.strokeStyle=color;g.beginPath();g.moveTo(390,250);for(let i=0;i<11;i++)g.lineTo(390+i*24,245-Math.sin(i*.8)*60);g.stroke();g.fillStyle='#dceef6';g.font='36px monospace';g.fillText('JARVIS',w-180,h-40);});}
function makeScreen(parent,title,pos,color,sub){const frame=box(parent,title+' FRAME',[1.78,1.13,.16],pos,metal);const display=box(parent,title+' DISPLAY',[1.65,1,.03],[pos[0],pos[1],pos[2]+.09],black);const plane=new THREE.Mesh(new THREE.PlaneGeometry(1.58,.93),new THREE.MeshBasicMaterial({map:screenTexture(title,color,sub)}));plane.position.set(pos[0],pos[1],pos[2]+.12);parent.add(plane);addHotspot(display,{title,text:'Interactive JARVIS display. Tap to explore the feature details.',href:'#features'});return {frame,display,plane};}
function buildRoom(){
 const room=new THREE.Group();scene.add(room);
 // Room shell: open-front command chamber
 box(room,'Floor',[15,.22,14],[0,-.12,0],floorMat);box(room,'Rear wall',[15,5.6,.28],[0,2.7,-6.6],wallMat);box(room,'Left wall',[.28,5.6,14],[-7.5,2.7,0],wallMat);box(room,'Right wall',[.28,5.6,14],[7.5,2.7,0],wallMat);
 for(let x=-7;x<=7;x+=2.5){box(room,'Wall ribs',[.08,5.3,.1],[x,2.65,-6.4],steel);box(room,'Ceiling ribs',[.14,.24,13.8],[x,5.28,0],metal);}
 box(room,'Ceiling edge',[15,.18,.22],[0,5.45,-6.4],metal);
 // Window bays and luminous city silhouettes
 for(let z=-4.7;z<=2.5;z+=3.5){box(room,'Window frame',[.18,2.8,2.6],[-7.28,3.1,z],metal);box(room,'Window glass',[.06,2.5,2.35],[-7.17,3.1,z],glass);for(let k=0;k<8;k++){const h=.35+(k%4)*.28;box(room,'City light',[.03,h,.04],[-7.12,1.9+h/2,z-1.05+k*.3],cyan);}}
 for(let x=-6.8;x<=6.8;x+=1.7)box(room,'Floor seam',[.018,.01,13.5],[x,.005,0],steel);
 tube(room,'Cyan floor guide',[[-6.7,.04,5.8],[-6.7,.04,2],[-5.5,.04,-2],[-5.5,.04,-5.8]],.025,cyan);tube(room,'Crimson floor guide',[[6.7,.04,5.8],[6.7,.04,2],[5.5,.04,-2],[5.5,.04,-5.8]],.025,redGlow);
 // Back-wall energy centerpiece, reactor-inspired but original
 box(room,'Core display wall',[6.8,4.7,.12],[0,2.75,-6.35],black);for(let x=-3;x<=3;x+=1.5)box(room,'Panel seams',[.035,4.3,.03],[x,2.75,-6.25],steel);
 ring(room,'Outer energy ring',1.52,.075,[0,3.25,-6.15],redGlow);ring(room,'Inner energy ring',1.14,.04,[0,3.25,-6.12],cyan);ring(room,'Core ring',.72,.12,[0,3.25,-6.08],whiteGlow);cylinder(room,'Core backing',.9,.9,.12,[0,3.25,-6.25],black,64);sphere(room,'Energy center',.48,[0,3.25,-6],whiteGlow,48);
 for(let i=0;i<8;i++){const a=i*Math.PI/4;const spoke=box(room,'Reactor spoke',[.14,.54,.12],[Math.cos(a)*1.3,3.25+Math.sin(a)*1.3,-6.08],steel);spoke.rotation.z=a;}
 // Main desk / console
 box(room,'Desk top',[6.2,.22,1.6],[0,1.15,-1.8],metal);box(room,'Desk fascia',[6,.95,.16],[0,.62,-1.1],black);box(room,'Desk left support',[.18,.95,1.45],[-2.9,.62,-1.8],metal);box(room,'Desk right support',[.18,.95,1.45],[2.9,.62,-1.8],metal);box(room,'Desk base',[5.4,.12,.15],[0,.12,-1.8],black);
 for(let x=-2.5;x<=2.5;x+=.5)box(room,'Control button',[.12,.04,.08],[x,1.28,-1.15],x<0?redGlow:cyan);
 makeScreen(room,'AI PROVIDERS',[-1.65,2.35,-2.35],'#62e7ff','CONNECTIONS / CONFIGURABLE');makeScreen(room,'VOICE + CHAT',[0,2.35,-2.35],'#ff4968','INPUT / OUTPUT');makeScreen(room,'LOCAL OPTIONS',[1.65,2.35,-2.35],'#8cecff','OPTIONAL SERVICES');
 for(const x of [-1.65,0,1.65])box(room,'Monitor stand',[.1,.8,.12],[x,1.75,-2.3],metal);
 // Side server cabinets
 for(const x of [-5.8,5.8]){box(room,'Server cabinet',[1.25,3.4,1],[x,1.7,-4.3],black);for(let y=.45;y<3.3;y+=.5){box(room,'Rack shelf',[1.05,.05,.75],[x,y,-3.75],metal);for(let i=0;i<6;i++)box(room,'Rack LED',[.04,.04,.03],[x-.42+i*.17,y+.1,-3.7],i%2?cyan:redGlow);}}
 // Product pedestal and floating orb assembly
 box(room,'Product plinth',[1.5,1.05,1.5],[3.8,.53,-.2],black);box(room,'Plinth cap',[1.62,.1,1.62],[3.8,1.1,-.2],steel);ring(room,'Plinth glow',.62,.04,[3.8,1.28,-.2],cyan);
 core=new THREE.Group();core.position.set(3.8,1.45,-.2);room.add(core);ring(core,'Orb outer ring',.44,.055,[0,0,0],redGlow);ring(core,'Orb inner ring',.34,.035,[0,.12,0],cyan);const orb=sphere(core,'JARVIS PRO energy orb',.24,[0,.1,0],whiteGlow);addHotspot(orb,{title:'JARVIS PRO',text:'Android AI companion APK: configurable providers, voice/chat and optional integrations. Features depend on setup and device.',href:'#pricing'});
 const holo=box(room,'Integration hologram',[.04,2.3,1.55],[5.3,2.4,-1.7],glass);holo.material=glass.clone();holo.material.transparent=true;holo.material.opacity=.72;addHotspot(holo,{title:'OPTIONAL INTEGRATIONS',text:'Integrations are not preconnected. Each requires compatible services and separate configuration.',href:'#setup'});
 // Chair, overhead fixtures, edge light strips, secondary hologram
 cylinder(room,'Chair post',.1,.1,.75,[0,.45,.4],metal);cylinder(room,'Chair base',.55,.55,.1,[0,.1,.4],black);box(room,'Chair seat',[1.05,.18,.95],[0,.85,.4],black);box(room,'Chair back',[1.05,1.2,.18],[0,1.45,.86],red);
 for(let x=-5.5;x<=5.5;x+=2.75)box(room,'Ceiling light',[1.2,.05,.5],[x,5.05,-2.5],whiteGlow);
 tube(room,'Rear red strip',[[-7,4.95,-6.2],[0,4.95,-6.2],[7,4.95,-6.2]],.035,redGlow);tube(room,'Left cyan strip',[[-7.35,4.9,5.8],[-7.35,4.9,-6.2]],.035,cyan);tube(room,'Right red strip',[[7.35,4.9,5.8],[7.35,4.9,-6.2]],.035,redGlow);
 const floatPanel=box(room,'Voice hologram',[.05,1.3,1.7],[-4.8,2.9,-2.3],glass);floatPanel.material=glass.clone();floatPanel.material.transparent=true;floatPanel.material.opacity=.5;addHotspot(floatPanel,{title:'VOICE + CHAT',text:'Chat, voice input and spoken responses when supported by the device and services.',href:'#features'});
 sphere(room,'Brand glint',.08,[0,4.65,-6.1],cyan).scale.set(1.4,.1,1.4);
 // Cinematic light rig
 scene.add(new THREE.HemisphereLight(0x7895bb,0x06080d,1.05));const key=new THREE.DirectionalLight(0x9bcfff,2.2);key.position.set(2,5,5);key.castShadow=!mobile;key.shadow.mapSize.set(1024,1024);key.shadow.camera.left=-10;key.shadow.camera.right=10;key.shadow.camera.top=10;key.shadow.camera.bottom=-10;scene.add(key);
 const redLight=new THREE.PointLight(0xff244e,50,12,2);redLight.position.set(-5,3,-3);scene.add(redLight);coreLight=new THREE.PointLight(0x40dfff,65,14,2);coreLight.position.set(0,3.8,-5.5);scene.add(coreLight);const deskLight=new THREE.PointLight(0x58dfff,28,7,2);deskLight.position.set(0,2.7,-1.6);scene.add(deskLight);
 room.traverse(o=>{if(o.isMesh){o.castShadow=!mobile;o.receiveShadow=!mobile;}});
}
function show(data){panelTitle.textContent=data.title;panelText.textContent=data.text;panelLink.href=data.href||'#features';panel.hidden=false;}
document.querySelector('#panel-close').addEventListener('click',()=>panel.hidden=true);
document.querySelectorAll('[data-jump]').forEach(b=>b.addEventListener('click',()=>document.querySelector(b.dataset.jump)?.scrollIntoView({behavior:reduced?'auto':'smooth'})));
document.querySelector('#enter-room').addEventListener('click',()=>{document.body.classList.add('entered');status.textContent='ENVIRONMENT ONLINE';const b=document.querySelector('#enter-room span');b.textContent='ROOM ONLINE';if(!reduced)document.querySelector('#features').scrollIntoView({behavior:'smooth'});});
document.querySelectorAll('.primary-cta').forEach(b=>b.addEventListener('click',()=>{document.body.classList.add('entered');status.textContent='ENVIRONMENT ONLINE';}));
function progress(){const max=document.documentElement.scrollHeight-innerHeight;return max>0?clamp(scrollY/max,0,1):0;}
function cameraUpdate(p){const q=p*(stops.length-1),i=Math.min(Math.floor(q),stops.length-2),f=smooth(q-i);camera.position.set(stops[i].p[0]+(stops[i+1].p[0]-stops[i].p[0])*f,stops[i].p[1]+(stops[i+1].p[1]-stops[i].p[1])*f,stops[i].p[2]+(stops[i+1].p[2]-stops[i].p[2])*f);const t=[0,1,2].map(k=>stops[i].t[k]+(stops[i+1].t[k]-stops[i].t[k])*f);camera.lookAt(t[0]+lookX,t[1]+lookY,t[2]);}
function updateSections(){let current=0;chapters.forEach((id,i)=>{const el=document.getElementById(id),r=el.getBoundingClientRect();if(r.top<innerHeight*.55&&r.bottom>innerHeight*.25)current=i;el.classList.toggle('visible',r.top<innerHeight*.78&&r.bottom>innerHeight*.12);});if(current!==chapter){chapter=current;coords.textContent='ROOM / 0'+(current+1);document.querySelectorAll('.chapter-dot').forEach((d,i)=>d.classList.toggle('active',i===current));}}
function animate(){requestAnimationFrame(animate);clock.getDelta();lookX+=(lookTX-lookX)*.04;lookY+=(lookTY-lookY)*.04;cameraUpdate(progress());updateSections();if(core)core.rotation.y+=.003;if(coreLight)coreLight.intensity=58+Math.sin(clock.elapsedTime*2.2)*5;renderer.render(scene,camera);}
function init(){try{renderer=new THREE.WebGLRenderer({canvas,antialias:!mobile,powerPreference:'high-performance'});}catch(e){fallback.hidden=false;loader.classList.add('hidden');window.__JARVIS_ROOM__={ready:false,renderer:'fallback'};return;}
renderer.setPixelRatio(Math.min(devicePixelRatio||1,mobile?1.25:1.65));renderer.setSize(innerWidth,innerHeight);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.05;renderer.shadowMap.enabled=!mobile;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
scene=new THREE.Scene();scene.background=new THREE.Color('#05070b');scene.fog=new THREE.FogExp2('#05070b',.028);camera=new THREE.PerspectiveCamera(48,innerWidth/innerHeight,.1,100);camera.position.set(...stops[0].p);clock=new THREE.Clock();raycaster=new THREE.Raycaster();mouse=new THREE.Vector2();try{buildRoom();loader.classList.add('hidden');window.__JARVIS_ROOM__={ready:true,renderer:'three',mobile,reducedMotion:reduced};animate();}
catch(e){console.error('Room renderer initialization failed',e);fallback.hidden=false;loader.classList.add('hidden');window.__JARVIS_ROOM__={ready:false,renderer:'fallback',error:String(e)};}}
window.addEventListener('pointermove',e=>{if(mobile)return;lookTX=(e.clientX/innerWidth-.5)*.32;lookTY=(.5-e.clientY/innerHeight)*.14;});
canvas.addEventListener('pointerdown',e=>{mouse.x=e.clientX/innerWidth*2-1;mouse.y=-(e.clientY/innerHeight)*2+1;raycaster.setFromCamera(mouse,camera);const hit=raycaster.intersectObjects(hotspots,true)[0];if(hit){let o=hit.object;while(o&&!o.userData.hotspot)o=o.parent;if(o&&o.userData.hotspot)show(o.userData.hotspot);}});
window.addEventListener('resize',()=>{if(!renderer)return;camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);renderer.setPixelRatio(Math.min(devicePixelRatio||1,mobile?1.25:1.65));});
init();