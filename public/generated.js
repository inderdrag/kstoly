'use strict';
(()=>{
 const entries=new Map();let database;
 const key=r=>JSON.stringify([r.title,r.ingredients.map(i=>[i.name,i.amount,i.unit]),r.steps.map(s=>[s.title,s.body])]);
 const ready=new Promise((resolve,reject)=>{const req=indexedDB.open('k-stolu-images',1);req.onupgradeneeded=()=>req.result.createObjectStore('photos',{keyPath:'key'});req.onerror=()=>reject(Error('Хранилище фото недоступно.'));req.onsuccess=()=>{database=req.result;const tx=database.transaction('photos'),all=tx.objectStore('photos').getAll();all.onsuccess=()=>{for(const x of all.result){if(x.blob instanceof Blob&&['image/jpeg','image/png'].includes(x.blob.type))entries.set(x.key,{...x,url:URL.createObjectURL(x.blob)});}resolve();};all.onerror=()=>reject(Error('Не удалось прочитать сохранённые фото.'));};});
 function transact(work){return new Promise((resolve,reject)=>{const tx=database.transaction('photos','readwrite');work(tx.objectStore('photos'));tx.oncomplete=resolve;tx.onerror=()=>reject(Error('Не удалось сохранить фото. Освободите место на устройстве.'));tx.onabort=tx.onerror;});}
 async function save(recipe,data,model){
  await ready;if(typeof data!=='string'||!/^data:image\/(jpeg|png);base64,/.test(data)||data.length>9*1024*1024)throw Error('Неподходящее изображение.');
  const image=await new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=()=>reject(Error('Не удалось открыть изображение.'));im.src=data;});
  if(!image.width||!image.height||image.width*image.height>16777216)throw Error('Слишком большое изображение.');
  const canvas=document.createElement('canvas'),ratio=Math.min(1,960/Math.max(image.width,image.height));canvas.width=Math.max(1,Math.round(image.width*ratio));canvas.height=Math.max(1,Math.round(image.height*ratio));const ctx=canvas.getContext('2d');ctx.fillStyle='#f8f7f0';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(image,0,0,canvas.width,canvas.height);
  const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/jpeg',.83));if(!blob||blob.size>2*1024*1024)throw Error('Не удалось сжать изображение.');
  const k=key(recipe);if(!entries.has(k)&&entries.size>=50)throw Error('Сохранено 50 фото. Удалите ненужные в настройках или меню рецепта.');
  const row={key:k,blob,model,created:Date.now()};await transact(s=>s.put(row));if(entries.has(k))URL.revokeObjectURL(entries.get(k).url);entries.set(k,{...row,url:URL.createObjectURL(blob)});return get(recipe);
 }
 function get(recipe){const x=entries.get(key(recipe));return x?{src:x.url,alt:recipe.title+' — изображение ИИ',generated:true,model:x.model}:null;}
 async function remove(recipe){await ready;const k=key(recipe);await transact(s=>s.delete(k));if(entries.has(k))URL.revokeObjectURL(entries.get(k).url);entries.delete(k);}
 async function clear(){await ready;await transact(s=>s.clear());for(const x of entries.values())URL.revokeObjectURL(x.url);entries.clear();}
 globalThis.DishImages={ready,key,get,save,remove,clear,count:()=>entries.size};ready.catch(()=>{});
})();
