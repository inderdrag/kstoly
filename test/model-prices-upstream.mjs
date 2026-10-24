import './pantry-upstream.mjs';
const original=globalThis.fetch;
globalThis.fetch=async(url,init)=>{const response=await original(url,init);const out=await response.json();const req=JSON.parse(init.body),raw=JSON.parse(out.choices[0].message.content);const text=req.messages[1].content[0].text;
 for(const r of raw.recipes||[]){r.ingredients[0].price={source:'estimate',quantity:1,unit:text.includes('BadUnit')?'pack':'kg',currency:'RUB',low:100,high:200,note:'Оценка, не магазин.'};if(text.includes('BadAmount'))r.ingredients[0].amount=0;}
 out.choices[0].message.content=JSON.stringify(raw);return Response.json(out);
};
