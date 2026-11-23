import * as THREE from 'three';
const canvas=document.querySelector('#world'),loader=document.querySelector('#loader'),hint=document.querySelector('#hint');
const nav=document.querySelector('#bottom-nav'),panel=document.querySelector('#content-panel'),panelTitle=document.querySelector('#panel-title'),panelCopy=document.querySelector('#panel-copy'),panelKicker=document.querySelector('#panel-kicker');
const mobile=matchMedia('(max-width:700px)').matches,reduce=matchMedia('(prefers-reduced-motion:reduce)').matches;
let renderer,scene,camera,clock,raycaster,mouse,doorPivot,door,doorHandle,roomLight,screenMeshes=[],hotspots=[],isInside=false,dragging=false,pointerStart={x:0,y:0},pointerLast={x:0,y:0},look={x:0,y:0},lookTo={x:0,y:0},camPos=new THREE.Vector3(),camTarget=new THREE.Vector3(),targetCurrent=new THREE.Vector3(),currentView='entrance',night=false;
const views={entrance:{position:[0,1.62,7.2],target:[0,1.65,4.35]},desk:{position:[1.15,1.58,-1.6],target:[1.25,1.55,-3.4]},shelf:{position:[2.6,1.6,-2.1],target:[3.6,1.5,-3.2]},piano:{position:[-2.15,1.58,-1.1],target:[-3.15,1.35,-2.1]}};
const mat=(color,rough=.7,metal=0)=>new THREE.MeshStandardMaterial({color,roughness:rough,metalness:metal});
const wall=mat('#292827'),sideWall=mat('#343332'),ceiling=mat('#d4cfc5'),floorMat=mat('#a7835f',.78),wood=mat('#8c6849'),woodDark=mat('#45372c'),black=mat('#171819',.34),metal=mat('#6b6c6b',.4,.65),fabric=mat('#202121'),white=mat('#e5e0d6'),warm=mat('#f7e6c8'),screenMat=new THREE.MeshBasicMaterial({color:'#183a66'});
function add(mesh,parent=scene){parent.add(mesh);return mesh}
function box(w,h,d,m,x=0,y=0,z=0){const a=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),m);a.position.set(x,y,z);a.castShadow=true;a.receiveShadow=true;return add(a)}
function cyl(r1,r2,h,m,x=0,y=0,z=0,n=24){const a=new THREE.Mesh(new THREE.CylinderGeometry(r1,r2,h,n),m);a.position.set(x,y,z);a.castShadow=true;return add(a)}
function sphere(r,m,x,y,z){const a=new THREE.Mesh(new THREE.SphereGeometry(r,20,12),m);a.position.set(x,y,z);a.castShadow=true;return add(a)}
function line(a,b,r,m){const d=new THREE.Vector3(...b).sub(new THREE.Vector3(...a));const o=new THREE.Mesh(new THREE.CylinderGeometry(r,r,d.length(),8),m);o.position.copy(new THREE.Vector3(...a).add(new THREE.Vector3(...b)).multiplyScalar(.5));o.quaternion.setFromUnitVectors(new THREE.Vector3(0,1,0),d.normalize());return add(o)}
function shadowBlob(x,z,sx,sz,opacity=.18){const c=document.createElement('canvas');c.width=c.height=128;const ctx=c.getContext('2d');const g=ctx.createRadialGradient(64,64,3,64,64,64);g.addColorStop(0,'rgba(0,0,0,.5)');g.addColorStop(1,'rgba(0,0,0,0)');ctx.fillStyle=g;ctx.fillRect(0,0,128,128);const t=new THREE.CanvasTexture(c);const m=new THREE.MeshBasicMaterial({map:t,transparent:true,opacity,depthWrite:false});const p=new THREE.Mesh(new THREE.PlaneGeometry(sx,sz),m);p.rotation.x=-Math.PI/2;p.position.set(x,.008,z);add(p)}
function makeFloorTexture(){const c=document.createElement('canvas');c.width=1024;c.height=1024;const ctx=c.getContext('2d');ctx.fillStyle='#9b7958';ctx.fillRect(0,0,1024,1024);for(let y=0;y<1024;y+=128){ctx.fillStyle=['#a98765','#967454','#aa8967','#9d7957'][Math.floor(y/128)%4];ctx.fillRect(0,y,1024,128);ctx.strokeStyle='rgba(48,28,12,.18)';ctx.lineWidth=2;ctx.strokeRect(0,y,1024,128);for(let i=0;i<40;i++){let x=Math.random()*1024;ctx.strokeStyle='rgba(44,25,12,.06)';ctx.beginPath();ctx.moveTo(x,y+Math.random()*128);ctx.bezierCurveTo(x+40,y+30,x-50,y+60,x+Math.random()*50,y+120);ctx.stroke()}}const t=new THREE.CanvasTexture(c);t.wrapS=t.wrapT=THREE.RepeatWrapping;t.repeat.set(2,2);t.colorSpace=THREE.SRGBColorSpace;return t}
function buildRoom(){const floor=new THREE.Mesh(new THREE.PlaneGeometry(9,10),floorMat);floor.rotation.x=-Math.PI/2;floor.position.set(0,0,-.5);floorMat.map=makeFloorTexture();floorMat.needsUpdate=true;add(floor);
box(9,.12,10,woodDark,0,-.06,-.5);box(9,.08,10,wood,0,-.1,-.5);
box(9,3.5,.18,wall,0,1.75,-5.5);box(.18,3.5,10,sideWall,-4.5,1.75,-.5);box(.18,3.5,10,sideWall,4.5,1.75,-.5);
box(9,.12,10,ceiling,0,3.5,-.5);
for(let s of [-1,1]){box(.14,.18,10,white,s*4.42,3.4,-.5);box(.14,.18,10,woodDark,s*4.42,.1,-.5)}
// Entrance wall split around door opening; door starts closed
box(1.6,3.5,.18,white,-3.7,1.75,4.55);box(1.6,3.5,.18,white,3.7,1.75,4.55);box(2.9,.5,.18,white,0,3.25,4.55);
doorPivot=new THREE.Group();doorPivot.position.set(-1.45,0,4.46);add(doorPivot);door=box(2.9,2.95,.12,mat('#d5d0c8'),1.45,1.48,0);doorPivot.add(door);door.position.set(1.45,1.48,0);door.userData.interactive=true;door.userData.title='Entrance';
const frame=box(3.05,3.08,.11,white,0,1.52,4.4);frame.visible=false;
doorHandle=new THREE.Group();doorHandle.position.set(2.62,1.42,.1);doorPivot.add(doorHandle);sphere(.055,metal,0,0,0);line([0,0,0],[.13,0,0],.025,metal);
box(9,.08,.14,woodDark,0,.07,4.47);
// trim and ceiling lamp
box(9,.12,.15,white,0,3.42,-.5);box(9,.1,.15,woodDark,0,.1,-5.38);
const lampCord=line([0,3.35,-.8],[0,2.8,-.8],.012,metal);sphere(.055,metal,0,2.8,-.8);const bulb=sphere(.07,warm,0,2.75,-.8);const shade=cyl(.16,.05,.13,woodDark,0,2.65,-.8,32);shade.position.y=2.65;const bulbLight=new THREE.PointLight('#ffebcc',9,7);bulbLight.position.set(0,2.74,-.8);add(bulbLight);
// Window and shutter on left
box(2.2,2.15,.12,woodDark,-4.37,2.05,-1.3);box(2.05,2,.04,mat('#8e9c9d',.3),-4.29,2.05,-1.3);box(.08,2.05,.08,white,-4.25,2.05,-1.3);box(2.05,.08,.08,white,-4.25,1.05,-1.3);box(.05,2,.05,white,-4.25,2.05,-1.3);
for(let i=0;i<8;i++){const shutter=box(.19,1.92,.04,white,-4.17,2.05,-2.15+i*.24);shutter.rotation.y=.15}
box(2.4,.65,.13,white,-4.3,.6,-1.3);for(let i=0;i<14;i++)box(.025,.55,.02,woodDark,-4.22,.6,-2.1+i*.12);
// desk & monitor cluster back-right
buildDesk();
// piano left
buildPiano();
// bookshelf right
buildShelf();
// wall art, framed panels
buildWallDetails();
}
function screenTexture(type='main'){const c=document.createElement('canvas');c.width=512;c.height=320;const ctx=c.getContext('2d');ctx.fillStyle=type==='main'?'#172a3c':'#101214';ctx.fillRect(0,0,512,320);if(type==='main'){ctx.fillStyle='#e5e6dc';ctx.font='bold 22px Arial';ctx.fillText('INTERACTIVE DISPLAY',28,44);ctx.fillStyle='#376baf';ctx.fillRect(25,80,250,200);ctx.fillStyle='#e7d3a0';ctx.fillRect(295,80,190,90);ctx.fillStyle='#fff';ctx.fillRect(295,188,190,92);ctx.fillStyle='#111';ctx.font='16px Arial';ctx.fillText('CONTENT',315,220)}else{ctx.strokeStyle='#4c89bc';ctx.lineWidth=2;for(let i=0;i<9;i++){ctx.beginPath();ctx.moveTo(28,32+i*28);ctx.lineTo(480,32+i*28);ctx.stroke()}ctx.fillStyle='#9ec6ef';ctx.fillRect(28,36,140,12);ctx.fillStyle='#e7c56c';ctx.fillRect(28,92,80,16)}return new THREE.CanvasTexture(c)}
function buildDesk(){const x=1.55,z=-3.65;box(2.5,.1,1.1,wood,x,1.03,z);box(2.45,.06,1.06,woodDark,x,1.1,z);for(let xx of [x-1.12,x+1.12])for(let zz of [z-.42,z+.42])box(.08,.94,.08,metal,xx,.55,zz);box(1.8,.07,.65,woodDark,x,.48,z);box(.7,.62,.1,woodDark,x+.68,.74,z+.35);
for(let i=0;i<2;i++){const mx=x-.55+i*.95;box(.7,.48,.06,black,mx,1.5,z-.36);const display=box(.62,.38,.025,screenMat,mx,1.5,z-.315);const tex=screenTexture(i?'secondary':'main');display.material=new THREE.MeshBasicMaterial({map:tex});screenMeshes.push(display);display.userData.interactive=true;display.userData.title=i?'Secondary Screen':'Desk Screen';display.userData.copy='Your future concept content will be placed on this screen.';box(.05,.26,.06,metal,mx,1.23,z-.36);box(.35,.04,.25,black,mx,1.1,z-.3)}
box(.75,.025,.35,black,x,1.17,z+.1);for(let i=0;i<16;i++)box(.035,.012,.035,white,x-.32+i*.043,1.19,z+.05);box(.75,.035,.25,black,x,1.13,z+.25);shadowBlob(x,z,3,1.8,.26);
// chair
cyl(.33,.33,.1,black,x,0.48,z+.85);box(.7,.65,.12,fabric,x,.9,z+1.05);line([x,.25,z+.85],[x,.45,z+.85],.045,metal);for(let i=0;i<5;i++){const a=i*Math.PI*2/5;line([x,.1,z+.85],[x+Math.cos(a)*.4,.1,z+.85+Math.sin(a)*.4],.03,metal);cyl(.05,.05,.06,black,x+Math.cos(a)*.4,.06,z+.85+Math.sin(a)*.4)}
// desktop small accessories
box(.22,.18,.18,black,x-1.0,1.18,z+.15);sphere(.045,white,x-1,1.28,z+.15);box(.35,.07,.25,black,x+1,1.15,z+.2);
}
function buildPiano(){const x=-3.1,z=-2.5;box(1.65,.52,.48,black,x,.7,z);box(1.72,.08,.5,woodDark,x,1,z);box(1.55,.09,.38,white,x,1.08,z+.02);for(let i=0;i<18;i++)box(.075,.05,.3,white,x-.7+i*.08,1.12,z+.04);for(let i=0;i<12;i++)if(i%7!==2&&i%7!==6)box(.05,.1,.17,black,x-.66+i*.12,1.17,z-.04);box(1.7,.5,.08,black,x,.8,z-.27);for(let legx of [x-.65,x+.65])box(.07,.65,.07,woodDark,legx,.35,z+.1);const bench=box(1.2,.14,.38,woodDark,x,.5,z+.85);for(let xx of [x-.5,x+.5])box(.06,.45,.06,metal,xx,.25,z+.85);shadowBlob(x,z,2.5,1.7,.22)}
function buildShelf(){const x=4.05,z=-3.6;box(.12,2.25,2.15,woodDark,x,1.25,z);for(let y of [.35,.95,1.55,2.15])box(.16, .06,2.05,wood,x-.05,y,z);for(let y of [.35,.95,1.55,2.15]){for(let i=0;i<4;i++){const item=box(.12,.35+Math.random()*.18,.2+Math.random()*.1,mat(['#c0b4a1','#4d5757','#a98d69','#8d8d84'][i]),x-.12,y+.18,z-.75+i*.45);item.rotation.z=(Math.random()-.5)*.12}}box(.2,.5,.2,white,x-.2,1.8,z-.75);box(.22,.24,.28,wood,x-.2,1.72,z+.5);shadowBlob(x-1,z,2.5,1.5,.2)}
function buildWallDetails(){// framed art on rear wall
for(let xx of [-2.9,-1.15]){box(.85,1.1,.08,woodDark,xx,2.2,-5.38);box(.72,.96,.04,white,xx,2.2,-5.32);const art=box(.62,.82,.02,mat(xx< -2?'#b6a890':'#777c7a'),xx,2.2,-5.28);art.rotation.z=.05}
box(1.1,.45,.12,woodDark,3.1,2.65,-5.38);box(.95,.32,.04,white,3.1,2.65,-5.3);box(.2,.08,.08,black,3.1,2.65,-5.26)}
function buildLighting(){scene.add(new THREE.HemisphereLight('#fff0db','#342e28',.7));const key=new THREE.PointLight('#ffe3b7',13,11);key.position.set(0,2.8,-1);add(key);const windowLight=new THREE.PointLight('#d5e7f2',3.5,7);windowLight.position.set(-4,2.5,-1.4);add(windowLight);const rear=new THREE.PointLight('#ffd8a5',4,6);rear.position.set(2,2.6,-4.5);add(rear);roomLight=new THREE.PointLight('#ffdfb5',6,9);roomLight.position.set(0,2.7,-.8);add(roomLight)}
function setView(name){const v=views[name]||views.desk;currentView=name;camPos.set(...v.position);camTarget.set(...v.target);lookTo.set(0,0);document.querySelectorAll('.location').forEach(b=>b.classList.toggle('active',b.dataset.view===name));panel.hidden=true;hint.textContent='Drag to look around';}
function animate(){requestAnimationFrame(animate);const dt=Math.min(clock.getDelta(),.05);const ease=reduce?1:.055;camera.position.lerp(camPos,ease);targetCurrent.lerp(camTarget,reduce?1:.055);const target=targetCurrent.clone();target.x+=look.x;target.y+=look.y;camera.lookAt(target);if(doorPivot){doorPivot.rotation.y+=(isInside?-1.12:0-doorPivot.rotation.y)*.045}renderer.render(scene,camera)}
function initialize(){try{renderer=new THREE.WebGLRenderer({canvas,antialias:!mobile,powerPreference:'high-performance'});}catch(e){loader.classList.add('done');hint.textContent='3D rendering unavailable';return}
renderer.setPixelRatio(Math.min(devicePixelRatio||1,mobile?1.3:1.8));renderer.setSize(innerWidth,innerHeight);renderer.shadowMap.enabled=true;renderer.shadowMap.type=THREE.PCFSoftShadowMap;renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.15;
scene=new THREE.Scene();scene.background=new THREE.Color('#292827');scene.fog=new THREE.Fog('#292827',14,28);camera=new THREE.PerspectiveCamera(52,innerWidth/innerHeight,.1,60);camera.position.set(...views.entrance.position);camera.lookAt(...views.entrance.target);clock=new THREE.Clock();raycaster=new THREE.Raycaster();mouse=new THREE.Vector2();buildRoom();buildLighting();setView('entrance');camPos.set(...views.entrance.position);camTarget.set(...views.entrance.target);targetCurrent.copy(camTarget);hint.textContent='Tap the door to enter';animate();loader.classList.add('done');window.__ROOM_TEMPLATE__={ready:true,renderer:'three',interactions:true};}
initialize();
function enterRoom(){isInside=true;setView('desk');camPos.set(0,1.62,2.4);camTarget.set(0,1.55,-2.5);hint.textContent='Tap the door to enter';}
canvas.addEventListener('pointerdown',e=>{dragging=true;pointerStart={x:e.clientX,y:e.clientY};pointerLast={x:e.clientX,y:e.clientY};canvas.setPointerCapture?.(e.pointerId)});
canvas.addEventListener('pointermove',e=>{if(!dragging)return;const dx=e.clientX-pointerLast.x,dy=e.clientY-pointerLast.y;lookTo.x=THREE.MathUtils.clamp(lookTo.x-dx/innerWidth*2.5,-1.3,1.3);lookTo.y=THREE.MathUtils.clamp(lookTo.y+dy/innerHeight*1.2,-.6,.6);look.x+=(lookTo.x-look.x)*.35;look.y+=(lookTo.y-look.y)*.35;pointerLast={x:e.clientX,y:e.clientY}});
canvas.addEventListener('pointerup',e=>{dragging=false;const dx=e.clientX-pointerStart.x,dy=e.clientY-pointerStart.y;if(Math.hypot(dx,dy)<10){mouse.x=(e.clientX/innerWidth)*2-1;mouse.y=-(e.clientY/innerHeight)*2+1;raycaster.setFromCamera(mouse,camera);const hits=raycaster.intersectObjects([door,...screenMeshes],false);if(hits.length){const obj=hits[0].object;if(obj===door&&!isInside){enterRoom();return;}panelTitle.textContent=obj.userData.title;panelCopy.textContent=obj.userData.copy||'This is a content placeholder. Your concept will be added here later.';panelKicker.textContent=obj===door?'ENTRANCE':'DESK / DISPLAY';panel.hidden=false;}}});
document.querySelectorAll('.location').forEach(b=>b.addEventListener('click',()=>{if(!isInside&&b.dataset.view!=='entrance')enterRoom();setView(b.dataset.view)}));
document.querySelector('#open-site').addEventListener('click',()=>{if(!isInside)enterRoom();panelTitle.textContent='Open site';panelKicker.textContent='INTERACTION';panelCopy.textContent='The final website destination and content will be connected when your concept is defined.';panel.hidden=false});
document.querySelector('#panel-close').addEventListener('click',()=>panel.hidden=true);
document.querySelector('#light-mode').addEventListener('click',()=>{night=!night;scene.background.set(night?'#111514':'#292827');roomLight.intensity=night?2:6;hint.textContent=night?'Night lighting':'Natural room lighting';});
window.addEventListener('resize',()=>{if(!renderer)return;camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);renderer.setPixelRatio(Math.min(devicePixelRatio||1,mobile?1.3:1.8))});
setTimeout(()=>{hint.style.opacity='.3'},8000);
