import test from 'node:test';
import assert from 'node:assert/strict';
import {readRequest,readModelResponse,traceSelection} from '../src/transport.js';
const request=(value,headers={})=>({headers:{'content-type':'application/json',...headers},async *[Symbol.asyncIterator](){yield Buffer.from(value);}});
test('request validates root, content type and bounded body',async()=>{
 for(const bad of ['null','[]','"value"','{'])await assert.rejects(readRequest(request(bad)),{status:400});
 await assert.rejects(readRequest(request('{}',{'content-type':'text/plain'})),{status:415});
 await assert.rejects(readRequest(request('{}',{'content-length':'100'}),10),{status:413});
 await assert.rejects(readRequest(request('{"large":true}'),5),{status:413});
 assert.deepEqual(await readRequest(request('{}')),{});
});
test('interrupted photo upload is 408, not an unclassified 500',async()=>{
 const r={headers:{'content-type':'application/json'},async *[Symbol.asyncIterator](){yield Buffer.from('{');throw Object.assign(new Error('private socket details'),{code:'ECONNRESET'});}};
 await assert.rejects(readRequest(r),e=>e.status===408&&!e.message.includes('private')&&e.message.includes('не отправлен'));
});
test('provider abort after headers is 504 and cannot fall into recipe retry',async()=>{
 const r=new Response(new ReadableStream({start(c){c.enqueue(new TextEncoder().encode('{'));c.error(new DOMException('secret provider details','AbortError'));}}));
 await assert.rejects(readModelResponse(r),e=>e.status===504&&!e.message.includes('secret')&&e.message.includes('55 секунд'));
});
test('provider body stream failure is 502',async()=>{
 const r=new Response(new ReadableStream({start(c){c.error(new TypeError('private details'));}}));
 await assert.rejects(readModelResponse(r),{status:502});
});
test('null, absent, invalid and oversized provider bodies are 502, not TypeError',async()=>{
 for(const value of ['null','[]','{}','{','{"choices":[{"message":{"content":[null]}}]}','x'.repeat(256001)])await assert.rejects(readModelResponse(new Response(value)),{status:502});
 await assert.rejects(readModelResponse(new Response(null)),{status:502});
});
test('normal and text-block responses retain finish_reason',async()=>{
 for(const content of ['hello',[{type:'text',text:'he'},{type:'other'},{type:'text',text:'llo'}]])assert.deepEqual(await readModelResponse(Response.json({choices:[{message:{content},finish_reason:'stop'}]})),{raw:'hello',finish:'stop'});
});
test('diagnostics contain only ID, fixed stages, timings and safe error class',()=>{
 const logs=[],trace=traceSelection(x=>logs.push(JSON.parse(x)));trace.mark('upload');trace.mark('upstream_dispatch');trace.fail(new Error('PASSWORD photo address'),500);
 assert.match(trace.id,/^[a-f0-9]{8}$/);assert.equal(logs.at(-1).stage,'upstream_dispatch');assert.equal(logs.at(-1).status,500);assert.equal(JSON.stringify(logs).includes('PASSWORD'),false);
});
