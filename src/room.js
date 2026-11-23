import * as THREE from 'three';
const canvas=document.querySelector('#world'),loader=document.querySelector('#loader'),status=document.querySelector('#state'),roomIndex=document.querySelector('#room-index'),enter=document.querySelector('#enter'),fallback=document.querySelector('#fallback');
const mobile=matchMedia('(max-width:720px)').matches,reduce=matchMedia('(prefers-reduced-motion:reduce)').matches;
let renderer,scene,camera,clock,raycaster,mouse,reactor,reactorGlow,door,doorLight,view=0,scrollTarget=0,scrollNow=0,drag=false,last={x:0,y:0},look={x:0,y:0},lookTarget={x:0,y:0},interactive=[];
const stops=[{z:11.5,y:1.68},{z:6.5,y:1.62},{z:1.1,y:1.58},{z:-5.2,y:1.56},{z:-10.5,y:1.6}];
const mat=(color,metal=0,rough=.4,emissive=0)=>new THREE.MeshStandardMaterial({color,metalness:metal,roughness:rough,emissive:color,emissiveIntensity:emissive});
const cyan=mat('#48dfff',.2,.25,3),red=mat('#ff3156',.25,.3,2.4),white=mat('#d6e8f0',.7,.24,.05),dark=mat('#101821',.85,.3),black=mat('#05080e',.75,.22),glass= new THREE.MeshPhysicalMaterial({color:'#112c3a',metalness:.3,roughness:.12,transparent:true,opacity:.45});
function box(x,y,z,w,h,d,m){const o=new THREE.Mesh(new THREE.BoxGeometry(w,h,d),m);o.position.set(x,y,z);o.castShadow=!mobile;o.receiveShadow=true;scene.add(o);return o}
function cyl(x,y,z,r1,r2,h,m,n=32){const o=new THREE.Mesh(new THREE.CylinderGeometry(r1,r2,h,n),m);o.position.set(x,y,z);o.castShadow=!mobile;scene.add(o);return o}
function line(x,y,z,x2,y2,z2,m=cyan,r=.018){const o=new THREE.Mesh(new THREE.CylinderGeometry(r,r,Math.hypot(x2-x,y2-y,z2-z),8),m);o.position.set((x+x2)/2,(y+y2)/2,(z+z2)/2);o.quaternion.setFromUnitVectors(new THREE.Vector3(0,1,0),new THREE.Vector3(x2-x,y2-y,z2-z).normalize());scene.add(o);return o}
function glowPlane(x,y,z,w,h,m){const o=box(x,y,z,w,h,.035,m);return o}
function panel(x,y,z,w,h,rotation=0){const g=new THREE.Group();g.position.set(x,y,z);g.rotation.y=rotation;scene.add(g);const frame=new THREE.Mesh(new THREE.BoxGeometry(w+.12,h+.12,.18),dark);g.add(frame);const pane=new THREE.Mesh(new THREE.BoxGeometry(w,h,.025),glass);pane.position.z=.1;g.add(pane);const edge=new THREE.Mesh(new THREE.BoxGeometry(w,.025,.03),cyan);edge.position.y=h/2;edge.position.z=.13;g.add(edge);return g}
function buildArchitecture(){
 // Continuous long room with a raised end portal and luminous floor runway.
 box(0,-.15,-1,18,.3,28,mat('#080c12',.8,.32));box(0,8.8,-1,18,.3,28,mat('#070a10',.65,.36));
 for(const side of [-1,1]){box(side*9,4,-1,.3,8,28,mat(side===-1?'#0b111a':'#0a0f17',.65,.4));
 for(let z=10;z>=-12;z-=4){box(side*8.96,4,z,.035,7.4,3.4,mat('#17222d',.7,.38));box(side*8.78,4,z,.08,7.1,3.1,black);line(side*8.72,.4,z-1.5,side*8.72,7.6,z-1.5,side===-1?cyan:red,.025);box(side*8.72,4,z, .045,6.8,.04,white)}
 for(let y of [1.1,3.9,6.8]){line(side*8.5,y,-13,side*8.5,y,11,side===-1?cyan:red,.025)}
 }
 // floor grid tiles, subtle seams and runway
 for(let x=-8;x<=8;x+=2)line(x,.015,12,x,.015,-14,mat('#15212c',.45,.6),.008);
 for(let z=-13;z<=12;z+=2)line(-8,.015,z,8,.015,z,mat('#15212c',.45,.6),.008);
 for(const s of [-1,1]){box(s*5.5,.04,1,.08,.04,24,cyan);box(s*5.5,.04,-1,.08,.04,24,red);}
 // ceiling ribs, recessed strips and hanging lamps
 for(let z=10;z>=-12;z-=3.3){box(0,8.55,z,16,.16,.34,dark);box(0,8.45,z,12,.025,.1,cyan);for(let x of [-6,-3,3,6])box(x,8.1,z,1.4,.04,.5,white)}
 // huge segmented rear wall and luminous portal
 box(0,4,-13.5,18,8,.4,black);box(0,4,-13.2,12,7.1,.12,dark);
 for(let x=-5.5;x<=5.5;x+=1.1)box(x,4,-13.08,.035,6.6,.025,mat('#23303b',.4,.6));
 box(0,4,-12.9,7.5,5.8,.12,black);box(0,4,-12.78,7.2,5.5,.06,mat('#101c29',.65,.3));
 for(let s of [-1,1]){box(s*4,4,-12.65,.1,6.6,.1,cyan);box(s*4,4,-12.65,.14,6.6,.08,red)}
 box(0,7.25,-12.6,8,.12,.12,cyan);box(0,.75,-12.6,8,.12,.12,red);
 // wall alcoves and display bays
 for(let z of [7,3,-1,-5,-9]){panel(-7.5,4,z,2.2,3.3,Math.PI/2);panel(7.5,4,z,2.2,3.3,-Math.PI/2)}
}
function buildCenter(){
 // command island in the middle
 box(0,.55,-1,7.2,.6,2.8,dark);box(0,.88,-1,6.8,.08,2.4,black);
 for(let x of [-2.5,0,2.5]){const screen=box(x,1.65,-1.85,2.05,1.15,.08,black);screen.rotation.x=-.12;glowPlane(x,1.65,-1.78,1.82,.92,mat('#061b29',.2,.2,.45));for(let k=0;k<8;k++)line(x-0.75,1.35+k*.07,-1.74,x+.72,1.35+k*.07,-1.74,mat('#1e7189',.1,.3,.35),.008)}
 for(let x of [-2.9,2.9])box(x,.25,-.7,.18,.5,.18,white);
 // reactor pedestal in the deeper room
 cyl(0,.35,-7.5,2.1,2.1,.65,dark,64);cyl(0,.78,-7.5,1.65,1.65,.18,black,64);
 for(let r of [1.42,1.16,.88,.55]){const tor=new THREE.Mesh(new THREE.TorusGeometry(r,.035,8,96),cyan);tor.position.set(0,1.2,-7.5);scene.add(tor);interactive.push(tor)}
 reactor=new THREE.Group();reactor.position.set(0,1.55,-7.5);scene.add(reactor);
 for(let r of [1.1,.78,.48]){const t=new THREE.Mesh(new THREE.TorusGeometry(r,.055,12,96),r===.78?red:cyan);reactor.add(t)}
 const core=cyl(0,0,0,.38,.48,.38,mat('#9ef5ff',.4,.12,3),48);core.rotation.x=Math.PI/2;reactor.add(core);
 for(let i=0;i<12;i++){const a=i*Math.PI/6,bar=box(Math.cos(a)*1.3,1.3,Math.sin(a)*1.3,.16,.55,.2,dark);bar.rotation.y=-a;reactor.add(bar);const blade=box(Math.cos(a)*.98,.12,Math.sin(a)*.98,.08,.5,.08,cyan);blade.rotation.y=-a;reactor.add(blade)}
 reactorGlow=new THREE.PointLight('#41eaff',28,12);reactorGlow.position.set(0,0,0);reactor.add(reactorGlow);
 // side equipment pods, storage, seating
 for(let side of [-1,1]){for(let z of [4,0,-4,-8]){box(side*6.8,1.15,z,1.1,2.2,1.4,dark);box(side*6.8,2.3,z,1.1,.08,1.4,red);for(let n=0;n<5;n++)box(side*6.8,1.4+n*.22,z-.55,.72,.025,.02,cyan)}}
 // chairs and floating glass hologram
 for(let side of [-1,1]){box(side*3,.5,-4,.8,.5,.8,black);box(side*3,1.1,-4,.8,.9,.15,dark);line(side*3,.1,-4,side*3,.65,-4,white,.06)}
}
function buildLight(){scene.add(new THREE.HemisphereLight('#8bdfff','#080912',.55));const key=new THREE.PointLight('#72eaff',90,50);key.position.set(0,7,3);scene.add(key);const redFill=new THREE.PointLight('#ff234d',42,30);redFill.position.set(6,3,-8);scene.add(redFill);const blueFill=new THREE.PointLight('#1d9fff',50,28);blueFill.position.set(-6,3,-6);scene.add(blueFill);const back=new THREE.PointLight('#f1fcff',45,24);back.position.set(0,6,-13);scene.add(back);for(let z of [8,2,-4,-10]){const l=new THREE.SpotLight('#78e9ff',35,18,Math.PI/5,.4,1);l.position.set(0,7.8,z);l.target.position.set(0,0,z);scene.add(l,l.target)}}
function animate(){requestAnimationFrame(animate);const dt=clock.getDelta();scrollNow+=(scrollTarget-scrollNow)*(reduce?.14:.045);camera.position.z=THREE.MathUtils.lerp(stops[0].z,stops[4].z,scrollNow);camera.position.y=THREE.MathUtils.lerp(stops[0].y,stops[4].y,scrollNow);look.x+=(lookTarget.x-look.x)*.045;look.y+=(lookTarget.y-look.y)*.045;camera.lookAt(look.x,1.7+look.y,-8.4);if(reactor){reactor.rotation.y+=dt*.24;reactor.rotation.z=Math.sin(clock.elapsedTime*.35)*.06}if(reactorGlow)reactorGlow.intensity=24+Math.sin(clock.elapsedTime*2)*6;renderer.render(scene,camera)}
function init(){try{renderer=new THREE.WebGLRenderer({canvas,antialias:!mobile,alpha:false,powerPreference:'high-performance'});}catch(e){fallback.hidden=false;loader.classList.add('done');return}
 renderer.setPixelRatio(Math.min(devicePixelRatio||1,mobile?1.1:1.5));renderer.setSize(innerWidth,innerHeight);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.12;renderer.shadowMap.enabled=!mobile;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
 scene=new THREE.Scene();scene.background=new THREE.Color('#02040a');scene.fog=new THREE.FogExp2('#02040a',.016);camera=new THREE.PerspectiveCamera(48,innerWidth/innerHeight,.1,100);camera.position.set(0,1.7,11.5);clock=new THREE.Clock();raycaster=new THREE.Raycaster();mouse=new THREE.Vector2();buildArchitecture();buildCenter();buildLight();animate();window.__JARVIS_ROOM__={ready:true,renderer:'three'};loader.classList.add('done')}
init();
window.addEventListener('wheel',e=>{scrollTarget=THREE.MathUtils.clamp(scrollTarget+e.deltaY/innerHeight*.12,0,1);view=Math.min(4,Math.floor(scrollTarget*4));roomIndex.textContent='0'+(view+1)+' — 04';},{passive:true});
window.addEventListener('pointerdown',e=>{drag=true;last={x:e.clientX,y:e.clientY};canvas.setPointerCapture?.(e.pointerId)});
window.addEventListener('pointermove',e=>{if(drag){const dx=e.clientX-last.x,dy=e.clientY-last.y;if(mobile)scrollTarget=THREE.MathUtils.clamp(scrollTarget-dy/innerHeight*.7,0,1);lookTarget.x+=-dx/innerWidth*3;lookTarget.y+=dy/innerHeight*2;last={x:e.clientX,y:e.clientY};}else{lookTarget.x=(e.clientX/innerWidth-.5)*1.2;lookTarget.y=(.5-e.clientY/innerHeight)*.35}});
window.addEventListener('pointerup',()=>drag=false);window.addEventListener('pointercancel',()=>drag=false);
enter.addEventListener('click',()=>{scrollTarget=scrollTarget<.2?.48:0;status.textContent=scrollTarget>.2?'EXPLORING':'SYSTEM ONLINE';});
window.addEventListener('resize',()=>{if(!renderer)return;camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setSize(innerWidth,innerHeight);renderer.setPixelRatio(Math.min(devicePixelRatio||1,mobile?1.1:1.5))});
