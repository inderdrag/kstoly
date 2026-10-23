import {createHash} from 'node:crypto';
export function createPublicLimiter(db,{limit=3,windowMs=60000,now=Date.now}={}){
 db.exec('CREATE TABLE IF NOT EXISTS public_rate(client TEXT NOT NULL, bucket INTEGER NOT NULL, requests INTEGER NOT NULL, PRIMARY KEY(client,bucket))');
 return remote=>{const bucket=Math.floor(now()/windowMs),client=createHash('sha256').update(String(remote||'unknown')+':'+bucket).digest('hex');db.prepare('DELETE FROM public_rate WHERE bucket < ?').run(bucket-1);db.prepare('INSERT OR IGNORE INTO public_rate(client,bucket,requests) VALUES(?,?,0)').run(client,bucket);const ok=db.prepare('UPDATE public_rate SET requests=requests+1 WHERE client=? AND bucket=? AND requests<?').run(client,bucket,limit).changes===1;return {ok,retryAfter:Math.max(1,Math.ceil(((bucket+1)*windowMs-now())/1000))};};
}
