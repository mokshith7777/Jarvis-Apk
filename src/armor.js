import * as THREE from 'three';
import {RoundedBoxGeometry} from 'three/addons/geometries/RoundedBoxGeometry.js';

const gold=new THREE.MeshStandardMaterial({color:0x9d351f,metalness:.88,roughness:.23});
const red=new THREE.MeshStandardMaterial({color:0x7e171d,metalness:.9,roughness:.28});
const goldBright=new THREE.MeshStandardMaterial({color:0xc98537,metalness:.86,roughness:.2});
const dark=new THREE.MeshStandardMaterial({color:0x151e27,metalness:.78,roughness:.3});
const graphite=new THREE.MeshStandardMaterial({color:0x303b47,metalness:.82,roughness:.27});
const glow=new THREE.MeshStandardMaterial({color:0x8cecff,metalness:.45,roughness:.18,emissive:0x21b9f7,emissiveIntensity:2.4});
const redGlow=new THREE.MeshStandardMaterial({color:0xff4668,emissive:0x740e23,emissiveIntensity:1.2,metalness:.4,roughness:.22});
const jointMat=new THREE.MeshStandardMaterial({color:0x05080d,metalness:.65,roughness:.45});
const parts=[];
function rounded(parent,name,size,pos,mat,bevel=.08){const mesh=new THREE.Mesh(new RoundedBoxGeometry(size[0],size[1],size[2],3,bevel),mat);mesh.name=name;mesh.position.set(...pos);mesh.castShadow=true;mesh.receiveShadow=true;parent.add(mesh);return mesh}
function sphere(parent,name,scale,pos,mat){const mesh=new THREE.Mesh(new THREE.SphereGeometry(1,32,24),mat);mesh.name=name;mesh.scale.set(...scale);mesh.position.set(...pos);mesh.castShadow=true;parent.add(mesh);return mesh}
function tube(parent,name,points,radius,mat){const curve=new THREE.CatmullRomCurve3(points.map(p=>new THREE.Vector3(...p)));const mesh=new THREE.Mesh(new THREE.TubeGeometry(curve,32,radius,8,false),mat);mesh.name=name;parent.add(mesh);return mesh}
function ring(parent,name,r,t,pos,mat){const mesh=new THREE.Mesh(new THREE.TorusGeometry(r,t,12,72),mat);mesh.name=name;mesh.position.set(...pos);mesh.castShadow=true;parent.add(mesh);return mesh}
function addPart(root,name,position,explode,make){const group=new THREE.Group();group.name=name;group.position.set(...position);group.userData.home=group.position.clone();group.userData.explode=new THREE.Vector3(...explode);make(group);root.add(group);parts.push(group);return group}
function reactor(parent){const g=new THREE.Group();g.name='ARC_REACTOR_ASSEMBLY';g.position.set(0,1.52,.47);parent.add(g);const back= new THREE.Mesh(new THREE.CylinderGeometry(.32,.36,.12,64),dark);back.rotation.x=Math.PI/2;g.add(back);
for(let i=0;i<4;i++){const tor=ring(g,'reactor-ring-'+i,.28-i*.045,.018,[0,0,.08+i*.028],i%2?goldBright:glow);tor.rotation.z=i*.28}
const lens=new THREE.Mesh(new THREE.CylinderGeometry(.17,.22,.08,48),glow);lens.rotation.x=Math.PI/2;lens.position.z=.14;g.add(lens);
const core=sphere(g,'reactor-core',[.13,.13,.075],[0,0,.2],new THREE.MeshStandardMaterial({color:0xc9faff,emissive:0x72dcff,emissiveIntensity:5,metalness:.2,roughness:.1}));
for(let i=0;i<12;i++){const a=i*Math.PI/6;const spoke=rounded(g,'radial-inductor-'+i,[.025,.095,.025],[Math.cos(a)*.235,Math.sin(a)*.235,.14],goldBright,.012);spoke.rotation.z=a}
const inner=ring(g,'inner-energy-ring',.105,.012,[0,0,.21],glow);inner.rotation.z=.2;
return g}
export function createArmor(){
const root=new THREE.Group();root.name='JARVIS_ARMOR_PROCEDURAL';const body=new THREE.Group();body.name='armor-rig';root.add(body);
const core= new THREE.Group();core.name='core-frame';body.add(core);
rounded(core,'spinal-frame',[.22,.9,.23],[0,1.45,-.03],dark,.08);
rounded(core,'sternum-frame',[.55,.78,.28],[0,1.58,.12],graphite,.09);
rounded(core,'abdominal-frame',[.44,.48,.22],[0,.98,.04],dark,.06);
for(let i=0;i<3;i++)rounded(core,'abdomen-segment-'+i,[.42-i*.035,.11,.24],[0,1.22-i*.14,.12],graphite,.035);
reactor(core);
addPart(body,'chest-armor',[0,1.55,.18],[0,.18,.58],g=>{
const chest=rounded(g,'upper-chest',[1.18,.65,.26],[0,.08,0],gold,.12);chest.rotation.z=0;
rounded(g,'sternum-ridge',[.18,.43,.12],[0,-.02,.17],goldBright,.04);
rounded(g,'left-chest-plate',[.48,.38,.12],[-.33,.06,.14],goldBright,.08).rotation.z=.16;
rounded(g,'right-chest-plate',[.48,.38,.12],[.33,.06,.14],goldBright,.08).rotation.z=-.16;
tube(g,'chest-edge-left',[[-.55,.25,.17],[-.4,.34,.16],[-.12,.27,.16]],.018,graphite);
tube(g,'chest-edge-right',[[.55,.25,.17],[.4,.34,.16],[.12,.27,.16]],.018,graphite);
});
addPart(body,'abdomen-shell',[0,1.02,.13],[0,.05,.38],g=>{
rounded(g,'upper-abdominal',[.54,.22,.24],[0,.12,0],red,.06);
rounded(g,'mid-abdominal',[.49,.2,.23],[0,-.1,.01],gold,.05);
rounded(g,'lower-abdominal',[.43,.17,.22],[0,-.29,.02],graphite,.05);
});
addPart(body,'pelvis',[0,.64,0],[0,-.16,.35],g=>{
rounded(g,'hip-core',[.7,.3,.38],[0,0,0],dark,.1);
rounded(g,'belt-front',[.62,.2,.34],[0,-.07,.16],goldBright,.05);
rounded(g,'pelvis-left',[.34,.25,.27],[-.32,-.12,.1],red,.06).rotation.z=.22;
rounded(g,'pelvis-right',[.34,.25,.27],[.32,-.12,.1],red,.06).rotation.z=-.22;
});
for(const side of [-1,1]){
const label=side<0?'left':'right';
addPart(body,label+'-shoulder',[side*.68,1.82,0],[side*.25,.22,.42],g=>{
sphere(g,label+'-shoulder-joint',[.23,.25,.23],[0,-.08,0],jointMat);
const p=rounded(g,label+'-shoulder-shell',[.45,.39,.43],[side*.08,.12,.04],goldBright,.1);p.rotation.z=-side*.3;
rounded(g,label+'-shoulder-trim',[.32,.12,.38],[side*.1,.25,.05],red,.04).rotation.z=-side*.3;
});
addPart(body,label+'-upper-arm',[side*.86,1.38,0],[side*.45,.12,.38],g=>{
sphere(g,label+'-elbow-joint',[.19,.18,.19],[0,-.29,0],jointMat);
const shell=rounded(g,label+'-bicep-shell',[.32,.52,.35],[0,-.05,.02],gold,.1);shell.rotation.z=-side*.12;
rounded(g,label+'-bicep-vent',[.18,.28,.12],[side*.04,-.03,.19],graphite,.04).rotation.z=-side*.12;
rounded(g,label+'-arm-inner',[.22,.43,.22],[0,-.08,-.12],dark,.06);
});
addPart(body,label+'-forearm',[side*.92,.89,.04],[side*.56,-.08,.48],g=>{
rounded(g,label+'-forearm-gauntlet',[.34,.5,.38],[0,-.02,.03],goldBright,.09).rotation.z=side*.13;
rounded(g,label+'-forearm-edge',[.36,.12,.4],[0,-.22,.02],red,.04);
rounded(g,label+'-forearm-panel',[.2,.3,.12],[0,.03,.22],red,.045);
for(let i=0;i<3;i++)rounded(g,label+'-vent-'+i,[.055,.15,.025],[side*.08+(i-1)*.06,.1,.26],dark,.012);
});
addPart(body,label+'-hand',[side*.92,.54,.1],[side*.62,-.13,.62],g=>{
sphere(g,label+'-palm-base',[.16,.2,.1],[0,-.05,.03],graphite);
rounded(g,label+'-palm-plate',[.22,.22,.12],[0,-.03,.1],gold,.06);
for(let i=0;i<4;i++){const finger=rounded(g,label+'-finger-'+i,[.065,.21,.07],[(i-1.5)*.07,-.22,.06],goldBright,.025);finger.rotation.z=(i-1.5)*.09}
rounded(g,label+'-thumb',[.07,.16,.07],[side*.19,-.08,.03],gold,.025).rotation.z=side*.5;
});
addPart(body,label+'-thigh',[side*.38,.3,0],[side*.22,-.32,.3],g=>{
sphere(g,label+'-hip-joint',[.22,.23,.2],[0,.18,0],jointMat);
rounded(g,label+'-thigh-outer',[.38,.62,.4],[side*.04,-.12,.04],gold,.1).rotation.z=side*.1;
rounded(g,label+'-thigh-front',[.27,.44,.14],[side*.04,-.13,.24],goldBright,.07).rotation.z=side*.08;
rounded(g,label+'-thigh-inset',[.16,.32,.1],[side*.04,-.15,.32],red,.04);
});
addPart(body,label+'-shin',[side*.4,-.36,.02],[side*.18,-.42,.32],g=>{
sphere(g,label+'-knee-joint',[.19,.17,.19],[0,.18,.02],jointMat);
rounded(g,label+'-knee-cap',[.33,.27,.34],[0,.18,.12],goldBright,.08);
rounded(g,label+'-shin-armor',[.34,.58,.36],[0,-.16,.03],red,.1);
rounded(g,label+'-shin-front',[.22,.42,.13],[0,-.13,.23],gold,.07);
rounded(g,label+'-shin-ridge',[.08,.38,.05],[0,-.15,.3],goldBright,.025);
});
addPart(body,label+'-boot',[side*.4,-.76,.18],[side*.25,-.55,.45],g=>{
rounded(g,label+'-ankle',[.22,.2,.22],[0,.13,0],dark,.04);
rounded(g,label+'-boot-shell',[.36,.35,.48],[0,-.05,.08],graphite,.08);
rounded(g,label+'-boot-toe',[.4,.16,.4],[0,-.2,.16],goldBright,.06);
rounded(g,label+'-sole',[.4,.07,.43],[0,-.29,.1],dark,.025);
});
}
addPart(body,'helmet',[0,2.28,.08],[0,.2,.55],g=>{
sphere(g,'helmet-shell',[.31,.38,.3],[0,.02,0],goldBright);
rounded(g,'helmet-faceplate',[.43,.32,.28],[0,-.11,.15],red,.1);
rounded(g,'face-mask',[.32,.21,.1],[0,-.19,.23],gold,.06);
rounded(g,'chin-plate',[.22,.09,.1],[0,-.27,.22],goldBright,.035);
const eyeMat=new THREE.MeshStandardMaterial({color:0xcdfbff,emissive:0x63dfff,emissiveIntensity:4,roughness:.1});
for(const s of [-1,1]){const eye=rounded(g,'eye-'+s,[.13,.035,.035],[s*.11,-.08,.25],eyeMat,.015);eye.rotation.z=s*.1}
rounded(g,'helmet-crown',[.32,.12,.24],[0,.25,.04],gold,.05);
for(const s of [-1,1])rounded(g,'temple-panel-'+s,[.12,.24,.19],[s*.27,.02,.02],red,.04);
});
const rear=addPart(body,'backpack-reactor',[0,1.45,-.25],[0,.18,-.5],g=>{
rounded(g,'backplate',[.55,.75,.18],[0,0,0],dark,.08);
for(let s of [-1,1])rounded(g,'power-cell-'+s,[.16,.52,.22],[s*.2,0,-.12],graphite,.04);
});
const visor=addPart(body,'visor',[0,2.28,.29],[0,.18,.75],g=>{
rounded(g,'visor-glass',[.3,.09,.025],[0,-.07,0],glow,.02);
});
root.userData.parts=parts;root.userData.core=core;root.userData.body=body;root.userData.reactor=core.getObjectByName('ARC_REACTOR_ASSEMBLY');
return root;
}
export function setExploded(root,amount){
for(const p of root.userData.parts){const home=p.userData.home,offset=p.userData.explode;p.position.copy(home).addScaledVector(offset,amount);p.rotation.x=amount*offset.z*.14;p.rotation.y=amount*offset.x*.12;p.rotation.z=amount*offset.y*.12}
}
export function disposeArmor(root){root.traverse(o=>{if(o.geometry)o.geometry.dispose();if(o.material&&!Array.isArray(o.material))o.material.dispose()})}
