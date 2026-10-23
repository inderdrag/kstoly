// Test-only transport: no network, no real keys, no photo logging.
if(process.env.NODE_ENV!=='test')throw new Error('Test transport requires NODE_ENV=test');
globalThis.fetch=async(url,init)=>{
 if(url!=='https://anymodel.org/v1/chat/completions')throw new Error('Unexpected external request');
 const data=JSON.parse(init.body),content=data.messages[1].content,text=content[0].text;
 console.log('TEST_UPSTREAM_CALL '+content.filter(x=>x.type==='image_url').length);
 if(text.includes('TestAbort'))return new Response(new ReadableStream({start(c){c.error(new DOMException('private upstream response','AbortError'));}}));
 if(text.includes('TestNull'))return Response.json(null);
 return Response.json({choices:[{message:{content:JSON.stringify({status:'not_food',message:'Тестовый ответ без внешней сети.'})},finish_reason:'stop'}]});
};
