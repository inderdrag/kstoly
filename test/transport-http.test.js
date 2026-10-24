import test from 'node:test';import assert from 'node:assert/strict';import {spawn} from 'node:child_process';import {mkdtemp,rm} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import net from 'node:net';
test('HTTP four maximum-size photos, receipt, interrupted response, validation and recovery',{timeout:30000},async()=>{
 const h=net.createServer();await new Promise(r=>h.listen(0,'127.0.0.1',r));const port=h.address().port;await new Promise(r=>h.close(r));const origin=`http://127.0.0.1:${port}`,dir=await mkdtemp(path.join(os.tmpdir(),'kstolu091-'));let logs='';
 const child=spawn(process.execPath,['--import','./test/transport-upstream.mjs','src/server.js'],{cwd:new URL('../',import.meta.url),env:{...process.env,NODE_ENV:'test',PUBLIC_BURST_LIMIT:'100',PORT:String(port),PUBLIC_ORIGIN:origin,APP_PASSWORD:'test-password-12345678',SESSION_SECRET:'not-production-secret-1234567890123456789',ANYMODEL_API_KEY:'fake-key-test-only',DATA_DIR:dir},stdio:['ignore','pipe','pipe']});child.stdout.on('data',x=>logs+=x);child.stderr.on('data',x=>logs+=x);
 try{
  let ready=false;for(let n=0;n<100;n++){try{if((await fetch(origin+'/api/status')).ok){ready=true;break;}}catch{}await new Promise(r=>setTimeout(r,50));}assert.equal(ready,true,logs);
  let cookie='';const send=data=>fetch(origin+'/api/analyze',{method:'POST',headers:{'Content-Type':'application/json',Origin:origin,Cookie:cookie},body:JSON.stringify(data)});
  const login=await fetch(origin+'/api/login',{method:'POST',headers:{'Content-Type':'application/json',Origin:origin},body:JSON.stringify({password:'test-password-12345678'})});assert.equal(login.status,200);cookie='';
  // Structurally valid JPEG headers padded to the allowed 2 MiB/photo, never sent externally.
  const b=Buffer.alloc(2*1024*1024);Buffer.from([255,216,255,192,0,17,8,0,16,0,16]).copy(b);b[b.length-2]=255;b[b.length-1]=217;const image='data:image/jpeg;base64,'+b.toString('base64');
  const payload={images:Array(4).fill(image),receipt:image,preferences:[],region:'TestCity',currency:'RUB',consent:true};
  assert.equal((await send(payload)).status,200);assert.match(logs,/TEST_UPSTREAM_CALL 5/);
  const single={image,region:'TestCity',currency:'RUB',consent:true};assert.equal((await send(single)).status,200);
  const callsBefore=(logs.match(/TEST_UPSTREAM_CALL/g)||[]).length;assert.equal((await send(null)).status,400);assert.equal((await send({...single,image:'not JPEG'})).status,400);assert.equal((logs.match(/TEST_UPSTREAM_CALL/g)||[]).length,callsBefore);
  let r=await send({...single,region:'TestAbort'});assert.equal(r.status,504);let out=await r.json();assert.equal(out.stage,'upstream_body');assert.match(out.requestId,/^[a-f0-9]{8}$/);assert.match(out.error,/время ожидания/);
  r=await send({...single,region:'TestNull'});assert.equal(r.status,502);assert.equal((await r.json()).stage,'upstream_body');
  assert.equal((await send(single)).status,200); // inFlight released after each failure
  assert.equal((logs.match(/TEST_UPSTREAM_CALL/g)||[]).length,5); // no automatic paid retries
  const traces=logs.split('\n').filter(x=>x.startsWith('{')).map(JSON.parse);assert.ok(traces.some(x=>x.event==='analyze_error'&&x.status===504));assert.equal(JSON.stringify(traces).includes('base64'),false);assert.equal(JSON.stringify(traces).includes('fake-key'),false);
  assert.equal((await(await fetch(origin+'/api/status')).json()).build,'1.2.3');
 }finally{const exited=new Promise(r=>child.once('exit',r));child.kill();await exited;await rm(dir,{recursive:true,force:true});}
});
