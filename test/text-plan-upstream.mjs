if(process.env.NODE_ENV!=='test')throw Error('Test-only');
import {appendFileSync} from 'node:fs';
const counts=new Map();
globalThis.fetch=async(url,init)=>{
 if(String(url)!=='https://anymodel.org/v1/chat/completions')throw Error('Unexpected external request');
 const req=JSON.parse(init.body);appendFileSync(process.env.QA_CAPTURE,JSON.stringify(req)+'\n');
 const text=req.messages[1].content[0].text;
 const key=(text.match(/"(Сбой один|Сбой всегда|Уточнить безопасность|Запросить фото|Правильный список)"/)||[])[1]||'legacy';
 counts.set(key,(counts.get(key)||0)+1);
 let result;
 if(key==='Сбой всегда'||(key==='Сбой один'&&counts.get(key)===1)||key==='legacy')result={status:'not_food',message:'На фото не удалось найти продукты. Снимите продукты на столе.'};
 else if(key==='Запросить фото'&&counts.get(key)===1)result={status:'need_details',message:'Загрузите фото продуктов.'};
 else if(key==='Уточнить безопасность')result={status:'need_details',message:'Уточните вид грибов: неизвестные дикие грибы использовать нельзя.'};
 else result={status:'ok',recipes:[{status:'ok',meal_type:'main',title:'Рис с томатами',description:'По подтверждённому списку.',servings:2,prep_minutes:5,cook_minutes:15,equipment:['hob'],ingredients:[{name:'Рис',amount:150,unit:'g',price:null,availability:'present',product_index:0}],steps:[{title:'Сварите рис',body:'Промойте рис и сварите в воде на слабом огне до мягкости. Время сверьте с упаковкой.',ingredient_indexes:[0],timer_seconds:900}],warnings:[]}]};
 return Response.json({choices:[{finish_reason:'stop',message:{content:JSON.stringify(result)}}]});
};
