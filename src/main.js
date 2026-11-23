import * as THREE from 'three';
import {GLTFLoader} from 'three/addons/loaders/GLTFLoader.js';
import {DRACOLoader} from 'three/addons/loaders/DRACOLoader.js';
import {KTX2Loader} from 'three/addons/loaders/KTX2Loader.js';
import {EffectComposer} from 'three/addons/postprocessing/EffectComposer.js';
import {RenderPass} from 'three/addons/postprocessing/RenderPass.js';
import {UnrealBloomPass} from 'three/addons/postprocessing/UnrealBloomPass.js';
import {ShaderPass} from 'three/addons/postprocessing/ShaderPass.js';
import {BokehPass} from 'three/addons/postprocessing/BokehPass.js';
import {RoomEnvironment} from 'three/addons/environments/RoomEnvironment.js';
import {createArmor,setExploded,setReactorExploded,disposeArmor} from './armor.js';
import Lenis from 'https://cdn.jsdelivr.net/npm/lenis@1.1.16/+esm';
import {gsap} from 'https://cdn.jsdelivr.net/npm/gsap@3.12.7/+esm';
import {ScrollTrigger} from 'https://cdn.jsdelivr.net/npm/gsap@3.12.7/ScrollTrigger/+esm';
gsap.registerPlugin(ScrollTrigger);
window.__JARVIS_RUNTIME__={status:'initializing',renderer:'three',startedAt:performance.now()};
const canvas=document.querySelector('#cinematic-world'),naturePhoto=document.querySelector('#nature-photo'),load=document.querySelector('#loading'),fallback=document.querySelector('#fallback');
naturePhoto?.addEventListener('error',()=>{naturePhoto.classList.add('nature-photo-failed');console.warn('Photographic backdrop failed; layered SVG fallback remains active')});if(naturePhoto?.complete&&!naturePhoto.naturalWidth)naturePhoto.classList.add('nature-photo-failed');
if(!canvas||!load||!fallback){window.__JARVIS_RUNTIME__={status:'fallback',renderer:'three',reason:'missing-runtime-dom'};if(fallback)fallback.hidden=false;if(load)load.classList.add('done');console.error('JARVIS 3D runtime: required scene DOM is missing');document.body.classList.add('cinematic-runtime-failed');throw new Error('Missing cinematic runtime DOM');}
const progressFill=document.querySelector('#progress-fill'),chapterNum=document.querySelector('#chapter-number'),chapterName=document.querySelector('#chapter-name'),hudPhase=document.querySelector('#hud-phase');
const mobile=matchMedia('(max-width: 700px)').matches||matchMedia('(pointer: coarse)').matches,reduceMotion=matchMedia('(prefers-reduced-motion: reduce)').matches,headless=/HeadlessChrome/i.test(navigator.userAgent);
let renderer;try{renderer=new THREE.WebGLRenderer({canvas,alpha:true,antialias:!mobile&&!headless,powerPreference:'high-performance'});}catch(error){window.__JARVIS_RUNTIME__={status:'fallback',renderer:'three',reason:'webgl-init-failed',error:String(error)};console.error('WebGL initialization failed',error);fallback.hidden=false;load.classList.add('done');clearTimeout(window.__jarvisReadyTimer);throw error;}
const gl=renderer.getContext(),debugRenderer=gl.getExtension('WEBGL_debug_renderer_info'),rendererName=debugRenderer?gl.getParameter(debugRenderer.UNMASKED_RENDERER_WEBGL):gl.getParameter(gl.RENDERER),softwareRenderer=headless||/swiftshader|llvmpipe|softpipe|software renderer/i.test(rendererName),lowPower=mobile||softwareRenderer;
renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,lowPower?1:1.8));renderer.setSize(innerWidth,innerHeight,false);renderer.setClearColor(0x000000,0);renderer.outputColorSpace=THREE.SRGBColorSpace;renderer.toneMapping=THREE.ACESFilmicToneMapping;renderer.toneMappingExposure=1.08;renderer.shadowMap.enabled=!lowPower;renderer.shadowMap.type=THREE.PCFSoftShadowMap;
const scene=new THREE.Scene();scene.background=null;scene.fog=new THREE.FogExp2(0x050b12,.055);const pmrem=new THREE.PMREMGenerator(renderer);const studioEnvironment=pmrem.fromScene(new RoomEnvironment(renderer),.04).texture;scene.environment=studioEnvironment;scene.environmentIntensity=1.15;pmrem.dispose();
const camera=new THREE.PerspectiveCamera(48,innerWidth/innerHeight,.08,220);camera.position.set(0,1.5,9.5);
const clock=new THREE.Clock(),state={target:0,progress:0,mouseX:0,mouseY:0,mouseTargetX:0,mouseTargetY:0,phase:0,paused:false};
const world=new THREE.Group();scene.add(world);
const ambient=new THREE.HemisphereLight(0x9bdfff,0x090a12,1.05);scene.add(ambient);
const key=new THREE.DirectionalLight(0xffc89a,4.2);key.position.set(-4,7,5);key.castShadow=!mobile;key.shadow.mapSize.set(1024,1024);key.shadow.camera.left=-5;key.shadow.camera.right=5;key.shadow.camera.top=6;key.shadow.camera.bottom=-5;scene.add(key);
const rim=new THREE.SpotLight(0x45caff,55,20,Math.PI/5,.55,1.2);rim.position.set(4,5,-3);rim.target.position.set(0,1.4,0);scene.add(rim,rim.target);
const redRim=new THREE.PointLight(0xff234f,18,8,2);redRim.position.set(-3,2.4,-2);scene.add(redRim);
const reactorLight=new THREE.PointLight(0x6deeff,5,4,2);reactorLight.position.set(0,1.55,1.1);scene.add(reactorLight);
function makeEnvironment(){
 const mountainWorld=new THREE.Group();scene.add(mountainWorld);const cloudWorld=new THREE.Group();scene.add(cloudWorld);const hazeMat={uniforms:{uTime:{value:0}}},ringLights=[];
 const noise=(x,z)=>{const ix=Math.floor(x),iz=Math.floor(z),fx=x-ix,fz=z-iz,ux=fx*fx*(3-2*fx),uz=fz*fz*(3-2*fz),h=(a,b)=>{const v=Math.sin(a*12.9898+b*78.233)*43758.5453;return v-Math.floor(v)};return THREE.MathUtils.lerp(THREE.MathUtils.lerp(h(ix,iz),h(ix+1,iz),ux),THREE.MathUtils.lerp(h(ix,iz+1),h(ix+1,iz+1),ux),uz)};
 const fbm=(x,z)=>noise(x,z)*.52+noise(x*2.1,z*2.1)*.26+noise(x*4.6,z*4.6)*.14+noise(x*9,z*9)*.08;
 function ridge(layer,width,depth,baseY,peak,seed,color,roughness=1){const nx=lowPower?90:180,nz=lowPower?32:64,positions=[],colors=[],indices=[],c=new THREE.Color(color);
  for(let iz=0;iz<=nz;iz++){const v=iz/nz,z=-depth/2+v*depth;for(let ix=0;ix<=nx;ix++){const u=ix/nx,x=-width/2+u*width,edge=Math.pow(Math.max(0,Math.sin(u*Math.PI)),.45),shape=Math.pow(Math.abs(Math.sin((x+seed)*.061+z*.025)),1.7)+Math.pow(Math.abs(Math.sin((x-seed)*.027-z*.04)),2.5)*.36,detail=fbm((x+seed)*.065,z*.12)-.5,y=baseY+edge*(peak*(.16+shape*.68)+detail*peak*.24*roughness)+Math.sin(z*.07+seed)*.5;positions.push(x,y,z);const shade=THREE.MathUtils.clamp(.72+detail*.23+edge*.16,0,1);colors.push(c.r*shade,c.g*shade,c.b*shade)}}
  for(let iz=0;iz<nz;iz++)for(let ix=0;ix<nx;ix++){const aa=iz*(nx+1)+ix,d=aa+nx+1;indices.push(aa,d,aa+1,aa+1,d,d+1)}
  const geo=new THREE.BufferGeometry();geo.setAttribute('position',new THREE.Float32BufferAttribute(positions,3));geo.setAttribute('color',new THREE.Float32BufferAttribute(colors,3));geo.setIndex(indices);geo.computeVertexNormals();const mesh=new THREE.Mesh(geo,new THREE.MeshStandardMaterial({vertexColors:true,roughness:1,metalness:0,side:THREE.DoubleSide,transparent:true,opacity:lowPower?.34:.48,depthWrite:false}));mesh.position.z=layer;mesh.receiveShadow=!mobile;mountainWorld.add(mesh)}
 ridge(-58,190,48,-19,36,4,0x849ba3,.45);ridge(-42,160,40,-16,30,19,0x607b83,.7);ridge(-29,130,34,-14,25,37,0x3c6262,1);ridge(-19,105,28,-12,21,51,0x274c42,1.15);ridge(-11,86,24,-10,16,72,0x17392c,1.35);
 const treeCount=lowPower?380:920,treeGeo=new THREE.ConeGeometry(.28,1.3,6),trunkGeo=new THREE.CylinderGeometry(.035,.055,.34,5),treeMat=new THREE.MeshStandardMaterial({color:0x123628,roughness:1,transparent:true,opacity:.48,depthWrite:false}),trunkMat=new THREE.MeshStandardMaterial({color:0x49392c,roughness:1,transparent:true,opacity:.4,depthWrite:false}),trees=new THREE.InstancedMesh(treeGeo,treeMat,treeCount),trunks=new THREE.InstancedMesh(trunkGeo,trunkMat,treeCount),dummy=new THREE.Object3D();let placed=0;
 for(let k=0;k<treeCount;k++){const x=(Math.random()-.5)*96,z=-9-Math.random()*13,y=-11+Math.max(0,Math.sin(x*.045+z*.08))*5+fbm(x*.08,z*.13)*3;if(y>-3)continue;const sc=.7+Math.random()*2;dummy.position.set(x,y+sc*.55,z);dummy.rotation.y=Math.random()*6.28;dummy.scale.set(sc*.72,sc,sc*.72);dummy.updateMatrix();trees.setMatrixAt(placed,dummy.matrix);dummy.position.set(x,y+sc*.08,z);dummy.scale.set(sc*.12,sc*.3,sc*.12);dummy.updateMatrix();trunks.setMatrixAt(placed,dummy.matrix);placed++}trees.count=placed;trunks.count=placed;mountainWorld.add(trees,trunks);
 const cloudMat=new THREE.MeshStandardMaterial({color:0xd5e1e6,transparent:true,opacity:.12,roughness:1,depthWrite:false}),cloudLit=new THREE.MeshStandardMaterial({color:0xf1f7f9,transparent:true,opacity:.18,roughness:1,depthWrite:false});
 const cloudBanks=lowPower?3:4,cloudPuffs=lowPower?14:22;for(let bank=0;bank<cloudBanks;bank++){const g=new THREE.Group();g.position.set((bank-(cloudBanks-1)/2)*24,1.5+bank*.8,-10-bank*9);cloudWorld.add(g);for(let n=0;n<cloudPuffs;n++){const m=new THREE.Mesh(new THREE.SphereGeometry(1,lowPower?10:16,lowPower?6:10),n%5===0?cloudLit:cloudMat);m.position.set((Math.random()-.5)*40,(Math.random()-.5)*3,(Math.random()-.5)*16);m.scale.set(3+Math.random()*5,1.1+Math.random()*2,3+Math.random()*5);g.add(m)}}
 const sky=new THREE.Mesh(new THREE.SphereGeometry(190,40,24),new THREE.MeshBasicMaterial({color:0x819aa2,side:THREE.BackSide}));scene.add(sky);const floor=new THREE.Mesh(new THREE.PlaneGeometry(220,220),new THREE.MeshStandardMaterial({color:0x07120e,roughness:1}));floor.rotation.x=-Math.PI/2;floor.position.y=-.12;floor.receiveShadow=true;scene.add(floor);
 return {platform:new THREE.Group(),ringLights,hazeMat,mountainWorld,cloudWorld};
}
const env=makeEnvironment();
const dustCount=lowPower?700:1500,dustPositions=new Float32Array(dustCount*3),dustSizes=new Float32Array(dustCount);
for(let i=0;i<dustCount;i++){dustPositions[i*3]=(Math.random()-.5)*22;dustPositions[i*3+1]=Math.random()*8;dustPositions[i*3+2]=(Math.random()-.5)*18-3;dustSizes[i]=Math.random()}
const dustGeo=new THREE.BufferGeometry();dustGeo.setAttribute('position',new THREE.BufferAttribute(dustPositions,3));dustGeo.setAttribute('aSize',new THREE.BufferAttribute(dustSizes,1));
const dustMat=new THREE.PointsMaterial({color:0x83dfff,size:mobile?.018:.026,transparent:true,opacity:.42,depthWrite:false,blending:THREE.AdditiveBlending});const dust=new THREE.Points(dustGeo,dustMat);scene.add(dust);
const armor=createArmor();world.add(armor);let activeArmor=armor;const armorMaterials=[];armor.visible=false;const reactorAssembly=armor.userData.reactor;
armor.traverse(o=>{if(o.isMesh){const source=Array.isArray(o.material)?o.material:[o.material],cloned=source.map(m=>{const c=m.clone();if(!reactorAssembly?.contains(o)){c.transparent=true;c.opacity=0;c.depthWrite=false;armorMaterials.push(c)}return c});o.material=Array.isArray(o.material)?cloned:cloned[0];o.castShadow=!mobile;o.receiveShadow=!mobile}});
// The procedural armor is deliberately hidden: the hero is the supplied cinematic reference image.
// Detach the detailed reactor assembly so the camera can reveal it as a separate 3D object.
const reactorObject=armor.userData.reactor;
if(reactorObject){reactorObject.parent?.remove(reactorObject);world.add(reactorObject);reactorObject.scale.setScalar(2.35);reactorObject.visible=false;}
const assetPipeline={draco:new DRACOLoader(),ktx2:new KTX2Loader().setTranscoderPath('https://cdn.jsdelivr.net/npm/three@0.170.0/examples/jsm/libs/basis/').detectSupport(renderer),gltf:new GLTFLoader()};
assetPipeline.draco.setDecoderPath('https://www.gstatic.com/draco/v1/decoders/');assetPipeline.gltf.setDRACOLoader(assetPipeline.draco);assetPipeline.gltf.setKTX2Loader(assetPipeline.ktx2);
function disposeLoaded(root){root.traverse(o=>{if(o.geometry)o.geometry.dispose();if(o.material){const mats=Array.isArray(o.material)?o.material:[o.material];mats.forEach(m=>{for(const k of ['map','normalMap','roughnessMap','metalnessMap','aoMap','emissiveMap'])if(m[k])m[k].dispose();m.dispose()})}})}
async function loadArmor(url,{replace=true,scale=1,position=[0,0,0]}={}){const gltf=await assetPipeline.gltf.loadAsync(url),model=gltf.scene;model.scale.setScalar(scale);model.position.set(...position);model.traverse(o=>{if(o.isMesh){o.castShadow=!mobile;o.receiveShadow=!mobile}});model.userData.homePosition=model.position.clone();const candidates=model.children.length>1?model.children:[];model.userData.explosionParts=candidates.map((part,i)=>{part.userData.home=part.position.clone();const box=new THREE.Box3().setFromObject(part),center=box.getCenter(new THREE.Vector3());let direction=center.clone();if(direction.length()<.2){const a=i*Math.PI*2/candidates.length;direction.set(Math.cos(a),Math.sin(a)*.35,.5)}direction.normalize().multiplyScalar(1.1+Math.min(box.getSize(new THREE.Vector3()).length(),1.5));part.userData.explode=direction;return part});if(replace&&activeArmor){world.remove(activeArmor);if(activeArmor!==armor)disposeLoaded(activeArmor)}world.add(model);activeArmor=model;return model}
window.JARVIS3D={loadArmor,restoreProcedural:()=>{if(activeArmor!==armor){world.remove(activeArmor);world.add(armor);activeArmor=armor}armor.visible=true;armorMaterials.forEach(m=>{m.opacity=1;m.depthWrite=true})},scene,camera,renderer,world,armor};
const beamGroup=new THREE.Group();scene.add(beamGroup);const beams=[];
for(let i=0;i<3;i++){const beam=new THREE.Mesh(new THREE.ConeGeometry(1.35,8,32,1,true),new THREE.MeshBasicMaterial({color:i===1?0x4bcfff:0x2c87bd,transparent:true,opacity:.045,depthWrite:false,blending:THREE.AdditiveBlending,side:THREE.DoubleSide}));beam.rotation.x=Math.PI;beam.position.set((i-1)*3.1,5.3,-1.6-i*.7);beamGroup.add(beam);beams.push(beam)}
const trailPositions=new Float32Array(armor.userData.parts.length*6);const trailGeo=new THREE.BufferGeometry();trailGeo.setAttribute('position',new THREE.BufferAttribute(trailPositions,3));const trailMat=new THREE.LineBasicMaterial({color:0x59dfff,transparent:true,opacity:.22,depthWrite:false,blending:THREE.AdditiveBlending});const fractureTrails=new THREE.LineSegments(trailGeo,trailMat);world.add(fractureTrails);
function updateTrails(amount){const arr=trailGeo.attributes.position.array;armor.userData.parts.forEach((part,i)=>{const h=part.userData.home,e=part.userData.explode;arr[i*6]=h.x;arr[i*6+1]=h.y;arr[i*6+2]=h.z;arr[i*6+3]=h.x+e.x*amount;arr[i*6+4]=h.y+e.y*amount;arr[i*6+5]=h.z+e.z*amount});trailGeo.attributes.position.needsUpdate=true;trailMat.opacity=.08+amount*.28;fractureTrails.visible=amount>.035}
const filmShader={uniforms:{tDiffuse:{value:null},uTime:{value:0},uAmount:{value:.0015},uAberration:{value:.0018}},vertexShader:'varying vec2 vUv;void main(){vUv=uv;gl_Position=projectionMatrix*modelViewMatrix*vec4(position,1.0);}',fragmentShader:'varying vec2 vUv;uniform sampler2D tDiffuse;uniform float uTime;uniform float uAmount;uniform float uAberration;float hash(vec2 p){return fract(sin(dot(p,vec2(12.9898,78.233)))*43758.5453);}void main(){vec2 d=vUv-.5;float r=length(d);vec2 off=d*uAberration*(1.0+r*2.0);vec3 c;c.r=texture2D(tDiffuse,vUv+off).r;c.g=texture2D(tDiffuse,vUv).g;c.b=texture2D(tDiffuse,vUv-off).b;float grain=(hash(vUv*vec2(1920.,1080.)+uTime)-.5)*uAmount;c+=grain;float vig=1.0-smoothstep(.42,.78,r)*.16;c*=vig;gl_FragColor=vec4(c,1.);}'};
const composer=new EffectComposer(renderer);composer.addPass(new RenderPass(scene,camera));const bloom=new UnrealBloomPass(new THREE.Vector2(innerWidth,innerHeight),mobile?0.35:softwareRenderer?0.3:0.72,.65,.22);if(!softwareRenderer)composer.addPass(bloom);const film=new ShaderPass(filmShader);if(!softwareRenderer)composer.addPass(film);let bokeh=null;if(!lowPower&&!reduceMotion){try{bokeh=new BokehPass(scene,camera,{focus:4.6,aperture:.00009,maxblur:.008});composer.addPass(bokeh)}catch(e){console.warn('DOF unavailable, continuing',e)}}
const chapters=[{label:'AWAKEN',phase:'01 / AWAKEN'},{label:'APPROACH',phase:'02 / APPROACH'},{label:'FRACTURE',phase:'03 / FRACTURE'},{label:'FLIGHT',phase:'04 / FLIGHT'},{label:'REACTOR',phase:'05 / REACTOR'}];
const cameraCurve=new THREE.CatmullRomCurve3([new THREE.Vector3(-.18,1.58,6.5),new THREE.Vector3(.05,1.66,5.6),new THREE.Vector3(0,1.58,4.45),new THREE.Vector3(0,1.52,3.25),new THREE.Vector3(0,1.52,2.05),new THREE.Vector3(0,1.52,1.2),new THREE.Vector3(0,1.52,.82)],false,'catmullrom',.42);
const targetCurve=new THREE.CatmullRomCurve3([new THREE.Vector3(0,1.24,0),new THREE.Vector3(0,1.35,.08),new THREE.Vector3(0,1.48,.18),new THREE.Vector3(0,1.52,.28),new THREE.Vector3(0,1.52,.42),new THREE.Vector3(0,1.52,.48),new THREE.Vector3(0,1.52,.48)],false,'catmullrom',.4);
function smooth(a,b,x){const t=THREE.MathUtils.clamp((x-a)/(b-a),0,1);return t*t*(3-2*t)}
function phaseAt(p){if(p<.18)return 0;if(p<.5)return 1;if(p<.69)return 2;if(p<.84)return 3;return 4}
function applyCinematic(p){
 const motionTime=reduceMotion?0:clock.elapsedTime;
 const intro=reduceMotion?1:smooth(0,2.8,motionTime);
 const approach=smooth(.12,.48,p);
 const suitFracture=smooth(.54,.69,p);
 const reactorReveal=smooth(.49,.66,p);
 const reactorExplode=smooth(.66,.83,p);
 const flight=smooth(.78,.92,p);
 const reactorClose=smooth(.84,1,p);
 const armorReveal=reduceMotion?0:smooth(.49,.56,p)*(1-smooth(.78,.84,p));armor.visible=armorReveal>.005;armorMaterials.forEach(m=>{m.opacity=armorReveal;m.depthWrite=armorReveal>.98});
 const cp=cameraCurve.getPoint(THREE.MathUtils.clamp(p,0,1));
 const target=targetCurve.getPoint(THREE.MathUtils.clamp(p,0,1));
 camera.position.copy(cp);
 camera.position.x+=state.mouseX*(1-approach)*.16;
 camera.position.y+=state.mouseY*(1-approach)*.1;
 camera.lookAt(target);
 key.position.set(-4+state.mouseX*2,7+state.mouseY*1.2,5+state.mouseY*2);
 rim.position.set(4+state.mouseX*2.8,5+state.mouseY*1.4,-3);
 world.position.y=-.04+Math.sin(smooth(0,.12,p)*Math.PI)*.025;world.position.x=.46*(1-approach);
 world.scale.setScalar(.94+intro*.06-approach*.025);
 world.rotation.y=state.mouseX*.045*(1-suitFracture*.8);
 if(activeArmor===armor){
   setExploded(armor,suitFracture);
   updateTrails(suitFracture);fractureTrails.visible=armor.visible&&suitFracture>.035;
   armor.rotation.y=state.mouseX*.08*(1-suitFracture);
   armor.rotation.x=state.mouseY*.04;
 }else{
   activeArmor.rotation.y=state.mouseX*.05;
   const parts=activeArmor.userData.explosionParts||[];
   parts.forEach(part=>{part.position.copy(part.userData.home).addScaledVector(part.userData.explode,suitFracture);part.rotation.y=suitFracture*.18});
   const home=activeArmor.userData.homePosition||new THREE.Vector3();
   activeArmor.position.copy(home).add(new THREE.Vector3(0,-suitFracture*.08,0));
   fractureTrails.visible=false;
 }
 if(reactorObject){
   reactorObject.visible=reactorReveal>.015;
   reactorObject.rotation.z=motionTime*.18;
   reactorObject.scale.setScalar(2.35+reactorReveal*.32);
   setReactorExploded(reactorObject,reactorExplode);
 }
 reactorLight.intensity=4+reactorReveal*12+reactorExplode*8+reactorClose*12;
 redRim.intensity=16*(1-suitFracture*.55)+reactorReveal*3;
 key.intensity=1.15+intro*3.1-approach*1.35+reactorReveal*.4;
 rim.intensity=15+intro*40+suitFracture*24+reactorExplode*20;
 ambient.intensity=.35+intro*.7-reactorReveal*.25;
 env.hazeMat.uniforms.uTime.value=motionTime;
 env.mountainWorld.position.x=-p*18;env.mountainWorld.position.y=Math.sin(p*Math.PI*2)*.55;env.mountainWorld.rotation.y=p*.055;env.cloudWorld.position.x=-p*28;env.cloudWorld.position.y=Math.sin(p*4)*.55;env.cloudWorld.rotation.y=p*.075;env.cloudWorld.children.forEach((bank,i)=>{bank.position.x=(i-1)*24+Math.sin(motionTime*.045+i)*.28;bank.position.y=1.5+i*1.4+Math.sin(motionTime*.08+i*1.7)*.08;});
 dust.rotation.y=p*.08;dust.position.z=-flight*1.4;
 dust.material.opacity=.42+reactorExplode*.18;
 env.ringLights.forEach((r,i)=>{r.rotation.z=p*(i%2?-.3:.25);r.material.opacity=.4+reactorExplode*.25});
 beams.forEach((b,i)=>{b.rotation.z=Math.sin(motionTime*.13+i)*.09;b.material.opacity=.035+Math.sin(motionTime*.4+i)*.012+reactorExplode*.025});
 bloom.strength=(mobile?.34:.68)+suitFracture*.2+reactorExplode*.35+reactorClose*.32;
 film.uniforms.uTime.value=motionTime;
 film.uniforms.uAberration=.0015+suitFracture*.0015+reactorExplode*.003+reactorClose*.002;
 film.uniforms.uAmount=mobile?.001:.0016;
 if(bokeh){bokeh.uniforms.focus.value=5.4-approach*3.4+reactorClose*.5;bokeh.uniforms.aperture.value=.00006+approach*.00003+reactorExplode*.000015}
 const ph=phaseAt(p);
 if(ph!==state.phase){state.phase=ph;chapterNum.textContent=String(ph+1).padStart(2,'0');chapterName.textContent=chapters[ph].label;hudPhase.textContent=chapters[ph].phase}
 progressFill.style.width=(p*100).toFixed(2)+'%';document.documentElement.style.setProperty('--mountain-scroll',p.toFixed(3));if(sceneCopy)sceneCopy.style.opacity=1-smooth(.16,.34,p);
}
const lenis=new Lenis({smoothWheel:false,smoothTouch:false,lerp:1,gestureOrientation:'vertical',wheelMultiplier:1.0,prevent:node=>node?.closest?.('[data-native-scroll]')});
// Read the document scroll position directly. GSAP scrub was competing with
// the same state property and could leave the camera target stranded above zero
// after an instant scroll-to-top, especially on low-frame-rate software renderers.
function syncScrollTarget(){state.target=THREE.MathUtils.clamp(window.scrollY/Math.max(1,document.documentElement.scrollHeight-innerHeight),0,1)}
lenis.on('scroll',()=>{syncScrollTarget();ScrollTrigger.update()});window.addEventListener('scroll',syncScrollTarget,{passive:true});window.addEventListener('resize',syncScrollTarget,{passive:true});syncScrollTarget();gsap.ticker.add(t=>lenis.raf(t*1000));gsap.ticker.lagSmoothing(0);
// One reveal owner only: GSAP/ScrollTrigger. The CSS fallback keeps content visible if motion or WebGL fails.
if(!reduceMotion){document.body.classList.add('cinematic-motion-ready');gsap.utils.toArray('.reveal').forEach(element=>{gsap.fromTo(element,{autoAlpha:0,y:22,filter:'blur(3px)'},{autoAlpha:1,y:0,filter:'blur(0px)',duration:.65,ease:'power2.out',immediateRender:false,scrollTrigger:{trigger:element,start:'top 88%',toggleActions:'play none none reverse'}})})}const entryGate=document.querySelector('#entry-gate'),enterButton=document.querySelector('#enter-experience'),sceneCopy=document.querySelector('#scene-copy');
/* No invisible entry gate: scrolling is available immediately. */
enterButton?.addEventListener('click',()=>{entryGate?.classList.add('dismissed');state.entered=true;if(sceneCopy)gsap.fromTo(sceneCopy,{x:-18,opacity:0},{x:0,opacity:1,duration:1.15,ease:'power3.out'});});

// Chapter HUD is derived from the global scroll progress in applyCinematic().
window.addEventListener('pointermove',e=>{if(!reduceMotion){state.mouseTargetX=(e.clientX/innerWidth-.5)*2;state.mouseTargetY=(e.clientY/innerHeight-.5)*2}const light=document.querySelector('.cursor-light');if(light&&!reduceMotion)light.style.transform='translate('+e.clientX+'px,'+e.clientY+'px) translate(-50%,-50%)'},{passive:true});
let resizeTimer;window.addEventListener('resize',()=>{camera.aspect=innerWidth/innerHeight;camera.updateProjectionMatrix();renderer.setPixelRatio(Math.min(window.devicePixelRatio||1,lowPower?1:1.6));renderer.setSize(innerWidth,innerHeight,false);composer.setSize(innerWidth,innerHeight);clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{lenis.resize();ScrollTrigger.refresh()},120);});
document.addEventListener('visibilitychange',()=>{state.paused=document.hidden});
const audioButton=document.querySelector('#audio-toggle');let audioContext=null,oscillators=[],audioGain=null,audioStopTimer=null;
function startAudio(){const AudioContextCtor=window.AudioContext||window.webkitAudioContext;if(!AudioContextCtor){audioButton.querySelector('span:last-child').textContent='AUDIO UNAVAILABLE';audioButton.setAttribute('aria-pressed','false');return}if(audioStopTimer){clearTimeout(audioStopTimer);audioStopTimer=null}if(audioContext){audioGain.gain.setTargetAtTime(.035,audioContext.currentTime,.15);if(audioContext.state==='suspended')audioContext.resume().catch(()=>{});return}audioContext=new AudioContextCtor();audioGain=audioContext.createGain();audioGain.gain.value=.0001;audioGain.connect(audioContext.destination);for(const freq of [43,64,86]){const osc=audioContext.createOscillator(),gain=audioContext.createGain();osc.type='sine';osc.frequency.value=freq;gain.gain.value=freq===43?.35:.08;osc.connect(gain);gain.connect(audioGain);osc.start();oscillators.push(osc)}audioGain.gain.setTargetAtTime(.035,audioContext.currentTime,.8)}
function stopAudio(){if(!audioContext)return;audioGain.gain.setTargetAtTime(.0001,audioContext.currentTime,.25);audioStopTimer=setTimeout(()=>{oscillators.forEach(o=>{try{o.stop()}catch{}});audioContext.close();audioContext=null;oscillators=[];audioStopTimer=null},800)}
audioButton.addEventListener('click',()=>{const enabled=audioButton.getAttribute('aria-pressed')!=='true';audioButton.setAttribute('aria-pressed',String(enabled));audioButton.querySelector('span:last-child').textContent=enabled?'AUDIO ON':'AUDIO OFF';if(enabled){startAudio();if(!audioContext)return}else stopAudio()});
const modelParam=new URLSearchParams(location.search).get('model');if(modelParam){const modelUrl=new URL(modelParam,location.href);if(modelUrl.origin!==location.origin||!(/\.(glb|gltf)$/i.test(modelUrl.pathname)))console.warn('Custom model rejected: use a same-origin GLB/GLTF file');else loadArmor(modelUrl.href).catch(err=>console.warn('Custom model failed; procedural armor remains',err))}
renderer.domElement.addEventListener('webglcontextlost',e=>{e.preventDefault();state.paused=true;const runtime=window.__JARVIS_RUNTIME__||{};runtime.status='context-lost';runtime.lastError='webgl-context-lost';window.__JARVIS_RUNTIME__=runtime;fallback.hidden=false;fallback.querySelector('p').textContent='Graphics context lost. Recovery is in progress; the storefront remains available.';cancelAnimationFrame(raf)});renderer.domElement.addEventListener('webglcontextrestored',()=>{try{renderer.resetState();renderer.setSize(innerWidth,innerHeight,false);composer.setSize(innerWidth,innerHeight);bloom.setSize(innerWidth,innerHeight);if(bokeh)bokeh.setSize(innerWidth,innerHeight);state.paused=false;fallback.hidden=true;ScrollTrigger.refresh();const runtime=window.__JARVIS_RUNTIME__||{};runtime.status='active';runtime.contextRestorations=(runtime.contextRestorations||0)+1;runtime.lastRecoveredAt=performance.now();runtime.lastError=null;window.__JARVIS_RUNTIME__=runtime;render()}catch(error){const runtime=window.__JARVIS_RUNTIME__||{};runtime.status='fallback';runtime.reason='webgl-restore-failed';runtime.error=String(error);window.__JARVIS_RUNTIME__=runtime;console.error('WebGL restoration failed',error);fallback.hidden=false}});
let raf;function render(){raf=requestAnimationFrame(render);if(state.paused)return;const rawDelta=clock.getDelta(),delta=Math.min(rawDelta,.05);let scrollEase=1;if(reduceMotion){state.progress=state.target;state.mouseX=0;state.mouseY=0}else{scrollEase=1-Math.exp(-24*Math.max(rawDelta,0));const pointerEase=1-Math.exp(-8.5*Math.max(rawDelta,0));state.progress+=(state.target-state.progress)*scrollEase;state.mouseX+=(state.mouseTargetX-state.mouseX)*pointerEase;state.mouseY+=(state.mouseTargetY-state.mouseY)*pointerEase}applyCinematic(state.progress);if(softwareRenderer)renderer.render(scene,camera);else composer.render();const runtime=window.__JARVIS_RUNTIME__;if(runtime?.status==='active'){runtime.frames=(runtime.frames||0)+1;runtime.scrollProgress=state.progress;runtime.scrollTarget=state.target;runtime.lastDelta=delta;runtime.lastRawDelta=rawDelta;runtime.lastScrollEase=reduceMotion?1:scrollEase;runtime.armorVisible=armor.visible;runtime.renderPaused=state.paused;runtime.documentHidden=document.hidden;runtime.lastRenderAt=performance.now()}}
document.body.classList.add('cinematic-3d-ready');
render();window.__JARVIS_RUNTIME__={status:'active',renderer:'three',webglVersion:renderer.capabilities.isWebGL2?2:1,canvasId:'cinematic-world',mobile,reducedMotion:reduceMotion,softwareRenderer,rendererName,lowPower,contextRestorations:0,frames:0,startedAt:window.__JARVIS_RUNTIME__?.startedAt||performance.now(),activatedAt:performance.now()};document.querySelector('#load-progress').textContent='100%';clearTimeout(window.__jarvisReadyTimer);load.classList.add('done');window.addEventListener('load',()=>ScrollTrigger.refresh());
