import {createHash} from 'node:crypto';
const error=(message,status)=>Object.assign(new Error(message),{status});
const valid=id=>typeof id==='string'&&/^[a-zA-Z0-9_-]{16,80}$/.test(id);
/** Persist outcomes, never photographs. An interrupted provider call is never replayed. */
export function createSelectionJobs({db,validate,reserve,run,available=()=>true,now=()=>Date.now()}){
 const ttl=86400000;let active=false;
 db.exec('CREATE TABLE IF NOT EXISTS selection_jobs(id TEXT PRIMARY KEY,fingerprint TEXT NOT NULL,status TEXT NOT NULL,stage TEXT NOT NULL,result TEXT,error TEXT,created INTEGER NOT NULL)');
 db.prepare("UPDATE selection_jobs SET status='failed',error='Сервер перезапустился до сохранения ответа. Запрос мог быть оплачен. Повтор автоматически не отправлен.' WHERE status='pending'").run();
 function lookup(id){if(!valid(id))throw error('Неверный код подбора.',400);const row=db.prepare('SELECT * FROM selection_jobs WHERE id=?').get(id);if(!row||now()-row.created>ttl)return {status:'not_found',requestId:id};return {status:row.status,stage:row.stage,requestId:id,...(row.status==='done'?{result:JSON.parse(row.result)}:{}),...(row.status==='failed'?{error:row.error}:{})};}
 function start(data){
  if(!valid(data?.requestId))throw error('Неверный код подбора.',400);
  const id=data.requestId;const fingerprint=createHash('sha256').update(JSON.stringify(data)).digest('hex');
  db.prepare('DELETE FROM selection_jobs WHERE created<?').run(now()-ttl);
  const previous=db.prepare('SELECT fingerprint FROM selection_jobs WHERE id=?').get(id);
  if(previous){if(previous.fingerprint!==fingerprint)throw error('Код подбора уже принадлежит другим фотографиям или настройкам.',409);return lookup(id);}
  validate(data);if(active||!available())throw error('Другой подбор уже выполняется. Подождите его завершения.',429);
  if(!reserve())throw error('Лимит подборов на сегодня исчерпан. Новый день считается по UTC.',429);
  db.prepare('INSERT INTO selection_jobs VALUES(?,?,?,?,NULL,NULL,?)').run(id,fingerprint,'pending','validation',now());active=true;
  const mark=stage=>db.prepare("UPDATE selection_jobs SET stage=? WHERE id=? AND status='pending'").run(stage,id);
  const fail=e=>{db.prepare('UPDATE selection_jobs SET status=?,error=? WHERE id=?').run('failed',e.status?e.message:'Не удалось завершить подбор. Запрос мог быть оплачен. Повтор автоматически не отправлен.',id);};
  try{Promise.resolve(run(data,mark)).then(result=>{db.prepare('UPDATE selection_jobs SET status=?,stage=?,result=? WHERE id=?').run('done','success',JSON.stringify(result),id);}).catch(fail).finally(()=>{active=false;});}catch(e){fail(e);active=false;}
  return lookup(id);
 }
 return {start,lookup};
}
