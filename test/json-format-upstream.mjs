// Offline fixture only. Real credentials/network are never used.
import './pantry-upstream.mjs';
const original=globalThis.fetch;let calls=0;
globalThis.fetch=async(url,init)=>{
 const response=await original(url,init);const value=await response.json();const valid=value.choices[0].message.content;const mode=process.env.QA_FORMAT;calls++;
 let raw=valid,finish='stop';
 if(mode==='local')raw='```JSON\n'+valid.replace('Проверенный список','Проверенный\nсписок').replace(/}$/ ,',}')+'\n```';
 if(mode==='repair'&&calls===1||mode==='invalid')raw=valid.replace(',"description"','"description"');
 if(mode==='truncated'&&calls===1){raw=valid.slice(0,-5);finish='length';}
 if(mode==='schema'){const data=JSON.parse(valid);data.recipes[0].ingredients[0].amount=-10;raw=JSON.stringify(data).replace(/}$/ ,',}');}
 return Response.json({choices:[{finish_reason:finish,message:{content:raw}}]});
};
