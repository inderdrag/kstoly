import test from 'node:test';import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';import {mkdtemp,readFile,rm} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import net from 'node:net';
import {PLAN_PROMPT,assertPlanResult,planCorrection} from '../src/plan-prompt.js';import {SYSTEM_PROMPT,RECIPE_FORMAT} from '../src/prompt.js';
test('Text plan shares full recipe schema, not photo-recognition instructions',()=>{assert.ok(SYSTEM_PROMPT.includes(RECIPE_FORMAT));assert.match(PLAN_PROMPT,/СОСТАВЛЕНИЕ РЕЦЕПТОВ ПО ПОДТВЕРЖДЁННОМУ/);assert.doesNotMatch(PLAN_PROMPT,/Если снимок не о еде|Первые 0 фото|ТОЛЬКО если виден чек/);for(const field of ['nutrition','timer_seconds','ingredient_indexes','image_prompt','equipment'])assert.ok(PLAN_PROMPT.includes(field));assert.match(planCorrection('Тест'),/Сохрани список, технику/);});
test('Photo failure statuses are invalid for a text plan; genuine safety clarification remains valid',()=>{for(const status of ['not_food','bad_photo'])assert.throws(()=>assertPlanResult({status,message:'Тест'}));assert.throws(()=>assertPlanResult({status:'need_details',message:'Загрузите фото продуктов.'}));assert.doesNotThrow(()=>assertPlanResult({status:'need_details',message:'Уточните вид диких грибов.'}));});
test('HTTP plan sends text only, repairs wrong photo status once and preserves real clarification',async()=>{
 const h=net.createServer();await new Promise(r=>h.listen(0,'127.0.0.1',r));const port=h.address().port;await new Promise(r=>h.close(r));const origin=`http://127.0.0.1:${port}`,dir=await mkdtemp(path.join(os.tmpdir(),'kstolu-text-plan-')),capture=path.join(dir,'calls.jsonl');
 const child=spawn(process.execPath,['--import','./test/text-plan-upstream.mjs','src/server.js'],{env:{...process.env,NODE_ENV:'test',PORT:String(port),PUBLIC_ORIGIN:origin,ANYMODEL_API_KEY:'fake-test-only',DATA_DIR:dir,QA_CAPTURE:capture,DAILY_REQUEST_LIMIT:'50',PUBLIC_BURST_LIMIT:'200'},stdio:'ignore'});
 try{
  let started=false;for(let i=0;i<100;i++){try{if((await fetch(origin+'/api/status')).ok){started=true;break}}catch{}await new Promise(r=>setTimeout(r,50))}assert.ok(started);
  const base={mode:'plan',products:['Рис','Правильный список'],equipment:['hob'],onlyOwn:true,preferences:['no_salads'],region:'Россия',currency:'RUB',consent:true};
  const send=data=>fetch(origin+'/api/analyze',{method:'POST',headers:{'Content-Type':'application/json',Origin:origin},body:JSON.stringify(data)});
  const calls=async()=>{try{return(await readFile(capture,'utf8')).trim().split('\n').filter(Boolean).map(JSON.parse)}catch{return []}};
  let res=await send({...base,images:['forbidden']});assert.equal(res.status,400);assert.equal((await calls()).length,0);
  for(const [marker,n] of [['Правильный список',1],['Сбой один',2],['Запросить фото',2]]){
   const before=(await calls()).length;res=await send({...base,products:['Рис',marker]});assert.equal(res.status,200);const out=await res.json();assert.equal(out.status,'ok');assert.equal(out.kind,'plan');assert.equal(out.recipes[0].ingredients[0].availability,'present');const recent=(await calls()).slice(before);assert.equal(recent.length,n);
   for(const call of recent){assert.equal(call.messages[0].content,PLAN_PROMPT);assert.equal(call.messages[1].content.length,1);assert.equal(call.messages[1].content[0].type,'text');assert.match(call.messages[1].content[0].text,/Рис/);assert.match(call.messages[1].content[0].text,new RegExp(marker));assert.doesNotMatch(JSON.stringify(call),/image_url|Первые 0 фото/)}
   if(n===2){assert.equal(recent[1].messages[2].role,'assistant');assert.match(recent[1].messages[3].content,/ПО ПОДТВЕРЖДЁННОМУ ТЕКСТОВОМУ СПИСКУ/)}
  }
  let before=(await calls()).length;res=await send({...base,products:['Рис','Сбой всегда']});assert.equal(res.status,502);const err=await res.json();assert.match(err.error,/Список сохранён/);assert.equal((await calls()).length-before,2);
  before=(await calls()).length;res=await send({...base,products:['Рис','Уточнить безопасность']});assert.equal(res.status,200);const detail=await res.json();assert.equal(detail.status,'need_details');assert.match(detail.recipe.message,/дикие грибы/);assert.equal((await calls()).length-before,1);
  const image='data:image/jpeg;base64,'+(await readFile(new URL('./fixture.jpg',import.meta.url))).toString('base64');res=await send({image,region:'Россия',currency:'RUB',consent:true});assert.equal(res.status,200);assert.equal((await res.json()).status,'not_food');
 }finally{child.kill();await new Promise(r=>child.once('exit',r));await rm(dir,{recursive:true,force:true})}
});
