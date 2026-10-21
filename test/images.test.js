import test from 'node:test';import assert from 'node:assert/strict';import {DatabaseSync} from 'node:sqlite';import {readFileSync} from 'node:fs';import {createImageService,decodeImage} from '../src/images.js';
const jpeg=readFileSync(new URL('./fixture.jpg',import.meta.url)).toString('base64');
const recipe={status:'ok',title:'Рис с овощами',description:'Домашнее блюдо',image_prompt:'Cooked rice with vegetables, loose white rice grains mixed with small pieces of vegetables, served as a simple savory main dish.',servings:2,prep_minutes:5,cook_minutes:15,ingredients:[{name:'Рис',amount:100,unit:'g',price:null}],steps:[{title:'Приготовьте',body:'Сварите рис.'}],warnings:[]};
const request=id=>({requestId:id.padEnd(20,'x'),recipe,currency:'RUB',consent:true});
function harness({paid=false,sync=false,bad=false}={}){const db=new DatabaseSync(':memory:');let calls=[],clock=1000000000;const fetcher=async(url,init)=>{calls.push([url,init]);if(url.includes('/models/info'))return Response.json({id:'am/flux.2-klein-4b',kind:'image',billing:{unit:'image',coefficient:{input:paid?1:0,output:paid?1:0}}});if(init.method==='POST')return sync?Response.json({data:[{b64_json:jpeg}]}):Response.json({id:'provider-job-1'},{status:202});return Response.json({data:[bad?{url:'http://127.0.0.1/secret'}:{b64_json:jpeg}]});};return {db,calls,service:createImageService({db,key:'fake-test-key',model:'am/flux.2-klein-4b',fetcher,now:()=>clock,daily:2}),advance:n=>clock+=n};}
test('images: accepts only bounded PNG/JPEG base64; no remote URL',()=>{assert.match(decodeImage(jpeg),/^data:image\/jpeg/);for(const x of [null,'http://localhost/','PGh0bWw+eDwv aHRtbD4=','A'.repeat(9*1024*1024)])assert.throws(()=>decodeImage(x));});
test('images: paid or unverified route is blocked before generation',async()=>{const h=harness({paid:true});assert.equal((await h.service.options()).available,false);await assert.rejects(()=>h.service.start(request('a')),/Бесплатный тариф/);assert.equal(h.calls.filter(x=>x[1].method==='POST').length,0);h.db.close();});
test('images: consent and recipe required before upstream',async()=>{const h=harness();await assert.rejects(()=>h.service.start({...request('a'),consent:false}),/Подтвердите/);await assert.rejects(()=>h.service.start({...request('a'),recipe:{}}));assert.equal(h.calls.length,0);h.db.close();});
test('images: async jobs, idempotent creation, server quota, poll binding',async()=>{const h=harness();const a=await h.service.start(request('a'));assert.equal(a.status,'pending');const again=await h.service.start(request('a'));assert.equal(again.id,a.id);assert.equal(h.calls.filter(x=>x[1].method==='POST').length,1);await assert.rejects(()=>h.service.start(request('b')),/ещё создаётся/);assert.equal((await h.service.poll(a.id)).status,'done');await assert.rejects(()=>h.service.poll(a.id),/несколько секунд/);h.advance(5000);const b=await h.service.start(request('b'));await h.service.poll(b.id);await assert.rejects(()=>h.service.start(request('c')),/лимит генераций/);await assert.rejects(()=>h.service.poll('https://evil.example'),/не найдено/);h.db.close();});
test('images: generation prompt contains dish only, one image, no photo or secret',async()=>{const h=harness({sync:true});const out=await h.service.start(request('a'));assert.equal(out.status,'done');const body=JSON.parse(h.calls.find(x=>x[1].method==='POST')[1].body);assert.equal(body.n,1);assert.equal(body.background,true);assert.equal(body.response_format,'b64_json');assert.equal(body.size,'1024x1024');assert.ok(!body.prompt.includes('fake-test-key'));assert.ok(!body.prompt.includes(jpeg));assert.ok(body.prompt.includes('Рис'));assert.equal((await h.service.poll(out.id)).image,out.image);h.db.close();});
test('images: arbitrary provider image URLs never fetched',async()=>{const h=harness({bad:true});const out=await h.service.start(request('a'));await assert.rejects(()=>h.service.poll(out.id),/изображение/);assert.ok(h.calls.every(x=>x[0].startsWith('https://anymodel.org/v1/')));h.db.close();});
test('images: job expiry never creates a replacement generation',async()=>{const h=harness();const out=await h.service.start(request('a'));h.advance(86400001);await assert.rejects(()=>h.service.poll(out.id),/истекло/);assert.equal(h.calls.filter(x=>x[1].method==='POST').length,1);h.db.close();});
test('images: 503 diagnostics identify creation stage and release definite failed job',async()=>{
 const db=new DatabaseSync(':memory:');const fetcher=async(url,init)=>url.includes('/models/info')?Response.json({id:'am/flux.2-klein-4b',kind:'image',billing:{unit:'image',coefficient:{input:0,output:0}}}):new Response('DO_NOT_EXPOSE_PROVIDER_BODY',{status:503});
 const service=createImageService({db,key:'fake-test-key',model:'am/flux.2-klein-4b',fetcher});
 await assert.rejects(()=>service.start(request('creation503')),e=>e.imageJobFailed===true&&e.message.includes('Создание фото')&&e.message.includes('am/flux.2-klein-4b')&&e.message.includes('HTTP 503')&&!e.message.includes('DO_NOT_EXPOSE'));
 assert.equal((await service.start(request('creation503'))).status,'failed');db.close();
});
test('images: retrieval 503 preserves job for retrying GET without another generation',async()=>{
 const db=new DatabaseSync(':memory:');let posts=0;const fetcher=async(url,init)=>{if(url.includes('/models/info'))return Response.json({id:'am/flux.2-klein-4b',kind:'image',billing:{unit:'image',coefficient:{input:0,output:0}}});if(init.method==='POST'){posts++;return Response.json({id:'provider-existing'},{status:202});}return new Response('DO_NOT_EXPOSE_PROVIDER_BODY',{status:503});};
 const service=createImageService({db,key:'fake-test-key',model:'am/flux.2-klein-4b',fetcher});const job=await service.start(request('poll503'));
 await assert.rejects(()=>service.poll(job.id),e=>e.imageJobFailed!==true&&e.message.includes('Получение фото')&&e.message.includes('HTTP 503'));
 assert.equal((await service.start(request('poll503'))).id,job.id);assert.equal(posts,1);db.close();
});
test('images: explicitly selected alternative free model is accepted without default fallback',async()=>{
 const db=new DatabaseSync(':memory:');const model='am/flux.1-dev';let seen='';
 const fetcher=async(url)=>{seen=url;return Response.json({id:model,kind:'image',billing:{unit:'image',coefficient:{input:0,output:0}}});};
 const service=createImageService({db,key:'fake-test-key',model,fetcher});const options=await service.options();assert.equal(options.available,true);assert.equal(options.model,model);assert.ok(seen.endsWith(encodeURIComponent(model)));db.close();
});
function cloudHarness({free=true,account='a'.repeat(32),token='private-test-token',status=200,invalid=false}={}){
 const db=new DatabaseSync(':memory:');const calls=[];
 const service=createImageService({db,key:'unused-anymodel',cloudflareAccount:account,cloudflareToken:token,cloudflareFreePlan:free,fetcher:async(url,init)=>{calls.push([url,init]);return status===200?Response.json({success:true,result:{image:invalid?'https://evil.example/image':jpeg}}):new Response('PRIVATE_ERROR',{status});}});
 return {db,calls,service};
}
test('cloudflare: incomplete credentials or unconfirmed Free plan never calls upstream',async()=>{
 for(const config of [{free:false},{token:''},{account:'../invalid'}]){const h=cloudHarness(config);assert.equal((await h.service.options()).available,false);await assert.rejects(()=>h.service.start(request('blocked')));assert.equal(h.calls.length,0);h.db.close();}
});
test('cloudflare: one synchronous call, correct authentication, no source photos or fallback',async()=>{
 const h=cloudHarness();const options=await h.service.options();assert.equal(options.provider,'Cloudflare');assert.equal(options.available,true);
 const result=await h.service.start(request('cf-success'));assert.equal(result.status,'done');assert.match(result.image,/^data:image\/jpeg/);
 assert.equal(h.calls.length,1);const[url,init]=h.calls[0];assert.equal(url,'https://api.cloudflare.com/client/v4/accounts/'+'a'.repeat(32)+'/ai/run/@cf/black-forest-labs/flux-1-schnell');assert.equal(init.headers.Authorization,'Bearer private-test-token');const body=JSON.parse(init.body);assert.equal(body.steps,4);assert.ok(body.prompt.length<=2048);assert.ok(body.prompt.startsWith('Cooked rice with vegetables'));assert.ok(!/[А-Яа-я]/.test(body.prompt));assert.ok(!init.body.includes(jpeg));assert.ok(!init.body.includes('private-test-token'));
 assert.equal((await h.service.start(request('cf-success'))).id,result.id);assert.equal((await h.service.poll(result.id)).image,result.image);assert.equal(h.calls.length,1);h.db.close();
});
test('cloudflare: quota and provider failure never retry or fall back to AnyModel',async()=>{
 for(const status of [429,503,401]){const h=cloudHarness({status});await assert.rejects(()=>h.service.start(request('cf-fail')),e=>e.message.includes('Cloudflare HTTP '+status)&&!e.message.includes('PRIVATE_ERROR'));assert.equal((await h.service.start(request('cf-fail'))).status,'failed');assert.equal(h.calls.length,1);h.db.close();}
});
test('cloudflare: returned external URLs are rejected without fetching',async()=>{
 const h=cloudHarness({invalid:true});await assert.rejects(()=>h.service.start(request('cf-invalid')));assert.equal(h.calls.length,1);h.db.close();
});

test('cloudflare: legacy recipes cannot generate generic photos or consume quota',async()=>{
 const h=cloudHarness();const old=request('cf-legacy');delete old.recipe.image_prompt;
 try{await assert.rejects(()=>h.service.start(old),/новый подбор/);assert.equal(h.calls.length,0);assert.equal(h.db.prepare('SELECT COUNT(*) AS n FROM image_usage').get().n,0);}finally{old.recipe.image_prompt='Cooked rice with vegetables, loose white rice grains mixed with small pieces of vegetables, served as a simple savory main dish.';h.db.close();}
});
test('cloudflare: sandwich identity leads the prompt, not photographic decor or JSON',async()=>{
 const h=cloudHarness();const dish={...recipe,title:'Бутерброды с творожным сыром и авокадо',image_prompt:'Open-faced sandwiches: visible slices of bread spread with white cream cheese and topped with green avocado slices. The bread, white cheese layer and avocado topping are clearly visible.'};
 const out=await h.service.start({...request('cf-sandwich'),recipe:dish});assert.equal(out.status,'done');const prompt=JSON.parse(h.calls[0][1].body).prompt;assert.ok(prompt.startsWith('Open-faced sandwiches:'));for(const term of ['bread','white cream cheese','green avocado slices'])assert.ok(prompt.includes(term));assert.ok(!prompt.includes('ingredients":'));assert.ok(prompt.length<=2048);h.db.close();
});
