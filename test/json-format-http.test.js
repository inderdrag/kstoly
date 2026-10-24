import test from 'node:test';import assert from 'node:assert/strict';import {spawn} from 'node:child_process';import {mkdtemp,readFile,rm} from 'node:fs/promises';import os from 'node:os';import path from 'node:path';import net from 'node:net';
for(const mode of ['local','repair','truncated','invalid','schema'])test('JSON HTTP recovery: '+mode,{timeout:20000},async()=>{
 const socket=net.createServer();await new Promise(r=>socket.listen(0,'127.0.0.1',r));const port=socket.address().port;await new Promise(r=>socket.close(r));const origin=`http://127.0.0.1:${port}`,dir=await mkdtemp(path.join(os.tmpdir(),'json-format-')),capture=path.join(dir,'capture');
 const child=spawn(process.execPath,['--import','./test/json-format-upstream.mjs','src/server.js'],{env:{...process.env,NODE_ENV:'test',PORT:String(port),PUBLIC_ORIGIN:origin,PUBLIC_BURST_LIMIT:'100',ANYMODEL_API_KEY:'test-key',DATA_DIR:dir,QA_CAPTURE:capture,QA_FORMAT:mode},stdio:'ignore'});
 try{
  for(let i=0;i<100;i++){try{if((await fetch(origin+'/api/status')).ok)break}catch{}await new Promise(r=>setTimeout(r,30))}
  const payload={requestId:'json-format-'+mode+'-123456789',mode:'plan',products:['Томаты','Рис'],equipment:['hob'],onlyOwn:true,consent:true,region:'Россия',currency:'RUB'};
  const post=()=>fetch(origin+'/api/selection-jobs',{method:'POST',headers:{Origin:origin,'Content-Type':'application/json'},body:JSON.stringify(payload)});
  const first=await post();assert.ok([200,202].includes(first.status));let result;
  for(let i=0;i<100;i++){result=await(await fetch(origin+'/api/selection-jobs/'+payload.requestId)).json();if(result.status!=='pending')break;await new Promise(r=>setTimeout(r,20))}
  assert.equal(result.status,['invalid','schema'].includes(mode)?'failed':'done',JSON.stringify(result));
  if(result.status==='done')assert.equal(result.result.recipes[0].ingredients[0].amount,200);
  await post();for(let i=0;i<3;i++)await fetch(origin+'/api/selection-jobs/'+payload.requestId);
  const calls=(await readFile(capture,'utf8')).trim().split('\n').map(JSON.parse);assert.equal(calls.length,mode==='local'?1:2);
  for(const call of calls)assert.ok(!JSON.stringify(call).includes('image_url'));
  if(calls.length===2){assert.equal(calls[1].messages.at(-2).role,'assistant');assert.match(calls[1].messages.at(-2).content,/Томаты с рисом/);assert.match(calls[1].messages.at(-1).content,/весь исправленный подбор/);assert.match(calls[1].messages[1].content[0].text,/Томаты.*Рис/)}
 }finally{const end=new Promise(r=>child.once('exit',r));child.kill();await end;await rm(dir,{force:true,recursive:true})}
});
