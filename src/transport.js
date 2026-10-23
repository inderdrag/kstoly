// Only bounded JSON objects cross the HTTP boundary. Never log input or provider bodies.
const failure=(message,status)=>Object.assign(new Error(message),{status});
export async function readRequest(req,max=6*1024*1024){
 if(!String(req.headers['content-type']||'').startsWith('application/json'))throw failure('Ожидается JSON.',415);
 if(Number(req.headers['content-length'])>max)throw failure('Запрос слишком большой.',413);
 const chunks=[];let size=0;
 try{for await(const chunk of req){size+=chunk.length;if(size>max)throw failure('Запрос слишком большой.',413);chunks.push(chunk);}}
 catch(e){if(e.status)throw e;throw failure('Загрузка фотографий прервалась. Запрос к нейросети ещё не отправлен.',408);}
 let value;try{value=JSON.parse(Buffer.concat(chunks).toString());}catch{throw failure('Некорректный JSON.',400);}
 if(!value||typeof value!=='object'||Array.isArray(value))throw failure('Ожидается JSON-объект.',400);
 return value;
}
export async function readModelResponse(response){
 if(!response.body)throw failure('AnyModel вернул пустой ответ. Новый запрос автоматически не отправлен.',502);
 const chunks=[];let len=0,reader;
 try{
  reader=response.body.getReader();
  for(;;){const {done,value}=await reader.read();if(done)break;len+=value.byteLength;if(len>256000){await reader.cancel().catch(()=>{});throw failure('Слишком большой ответ AnyModel.',502);}chunks.push(value);}
 }catch(e){if(e.status)throw e;const timeout=['AbortError','TimeoutError'].includes(e.name);throw failure(timeout?'Ожидание ответа AnyModel превысило 55 секунд. Ответ не получен полностью. Новый запрос автоматически не отправлен.':'Соединение с AnyModel прервалось при получении ответа. Новый запрос автоматически не отправлен.',timeout?504:502);}
 finally{reader?.releaseLock();}
 let data;try{data=JSON.parse(Buffer.concat(chunks).toString());}catch{throw failure('AnyModel вернул непонятный ответ. Новый запрос автоматически не отправлен.',502);}
 if(!data||typeof data!=='object'||Array.isArray(data)||!Array.isArray(data.choices)||!data.choices[0]?.message)throw failure('AnyModel не вернул результат модели. Новый запрос автоматически не отправлен.',502);
 const raw=data.choices[0].message.content;
 if(typeof raw==='string')return {raw,finish:data.choices[0].finish_reason};
 if(Array.isArray(raw)&&raw.every(x=>x&&typeof x==='object'&&(x.type!=='text'||typeof x.text==='string')))return {raw:raw.filter(x=>x.type==='text').map(x=>x.text).join(''),finish:data.choices[0].finish_reason};
 throw failure('AnyModel вернул неподходящий формат результата. Новый запрос автоматически не отправлен.',502);
}
export function traceSelection(logger=console.log){
 const started=Date.now();let stage='upload';
 const id=globalThis.crypto.randomUUID().slice(0,8);
 const log=(event,extra={})=>logger(JSON.stringify({event,requestId:id,stage,ms:Date.now()-started,...extra}));
 return {id,get stage(){return stage;},mark(value){stage=value;log('analyze_stage');},fail(e,status){const allowed=['AbortError','TimeoutError','TypeError','RangeError','SyntaxError','RecipeError','Error'];log('analyze_error',{status,errorType:allowed.includes(e?.name)?e.name:'Error'});}};
}
