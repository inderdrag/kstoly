export class RecipeError extends Error {}
const obj=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const text=(x,n=600)=>typeof x==='string'&&x.trim().length>0&&x.length<=n;
const number=(x,min,max)=>typeof x==='number'&&Number.isFinite(x)&&x>=min&&x<=max;
export function validateRecipe(data,{receipt=false,currency='RUB'}={}) {
  const fail=()=>{throw new RecipeError('Ответ модели не соответствует структуре рецепта.');};
  if(!obj(data)||!['ok','bad_photo','not_food','need_details'].includes(data.status))fail();
  if(data.status!=='ok'){
    if(!text(data.message,600))fail();
    return {status:data.status,message:data.message};
  }
  if(!text(data.title,150)||!text(data.description,800)||!Number.isInteger(data.servings)||!number(data.servings,1,8)||!number(data.prep_minutes,0,240)||!number(data.cook_minutes,1,720))fail();
  if(!Array.isArray(data.ingredients)||data.ingredients.length<1||data.ingredients.length>30||!Array.isArray(data.steps)||data.steps.length<1||data.steps.length>15)fail();
  const ingredients=data.ingredients.map(i=>{
    if(!obj(i)||!text(i.name,100)||!number(i.amount,.001,100000)||!['g','ml','piece'].includes(i.unit))fail();
    let price=null;
    if(i.price!==null){
      const p=i.price;
      if(!obj(p)||!['estimate','receipt'].includes(p.source)||!number(p.quantity,.001,100000)||p.unit!==i.unit||p.currency!==currency||!number(p.low,0,1000000)||!number(p.high,0,1000000)||p.low>p.high||!text(p.note,400))fail();
      if(p.source==='receipt'&&(!receipt||p.low!==p.high))fail();
      price={source:p.source,quantity:p.quantity,unit:p.unit,currency:p.currency,low:p.low,high:p.high,note:p.note};
    }
    return {name:i.name,amount:i.amount,unit:i.unit,price};
  });
  const steps=data.steps.map(s=>{if(!obj(s)||!text(s.title,100)||!text(s.body,1200))fail();return {title:s.title,body:s.body};});
  if(!Array.isArray(data.warnings)||data.warnings.length>12||data.warnings.some(x=>!text(x,400)))fail();
  return {status:'ok',title:data.title,description:data.description,servings:data.servings,prep_minutes:data.prep_minutes,cook_minutes:data.cook_minutes,ingredients,steps,warnings:data.warnings};
}
export function parseRecipe(raw,options){
  if(typeof raw!=='string'||raw.length>60000)throw new RecipeError('Пустой или слишком большой ответ модели.');
  let source=raw.trim();
  if(source.startsWith('```'))source=source.replace(/^```(?:json)?\s*/,'').replace(/\s*```$/,'');
  let value;try{value=JSON.parse(source);}catch{throw new RecipeError('Модель вернула невалидный JSON.');}
  return validateRecipe(value,options);
}
export function calculateCost(recipe,servings=recipe.servings){
  if(!Number.isInteger(servings)||servings<1||servings>8)throw new RecipeError('Недопустимое число порций.');
  const factor=servings/recipe.servings;let low=0,high=0;const missing=[];
  const items=recipe.ingredients.map(i=>{
    const amount=i.amount*factor;if(!i.price){missing.push(i.name);return {name:i.name,amount,unit:i.unit,low:null,high:null};}
    const lo=i.price.low*amount/i.price.quantity,hi=i.price.high*amount/i.price.quantity;low+=lo;high+=hi;
    return {name:i.name,amount,unit:i.unit,low:lo,high:hi,source:i.price.source};
  });
  const round=x=>Math.round((x+Number.EPSILON)*100)/100;
  return {servings,low:round(low),high:round(high),perPortionLow:round(low/servings),perPortionHigh:round(high/servings),missing,hasPrices:missing.length<recipe.ingredients.length,hasEstimates:recipe.ingredients.some(i=>i.price?.source==='estimate'),items};
}
export function jpegInfo(dataUrl){
  if(typeof dataUrl!=='string'||dataUrl.length>2900000||!/^data:image\/jpeg;base64,[A-Za-z0-9+/]+={0,2}$/.test(dataUrl))throw new RecipeError('Нужно фото JPEG после сжатия.');
  const buffer=Buffer.from(dataUrl.slice(23),'base64');
  if(buffer.length<32||buffer.length>2*1024*1024||buffer[0]!==255||buffer[1]!==216||buffer.at(-2)!==255||buffer.at(-1)!==217)throw new RecipeError('Повреждённое фото или размер больше 2 МБ.');
  let offset=2;
  while(offset<buffer.length-8){
    if(buffer[offset++]!==255)break;while(buffer[offset]===255)offset++;
    const marker=buffer[offset++];if(marker===0xda||marker===0xd9)break;if(marker===0x01||(marker>=0xd0&&marker<=0xd7))continue;
    if(offset+2>buffer.length)break;const length=buffer.readUInt16BE(offset);if(length<2||offset+length>buffer.length)break;
    if([0xc0,0xc1,0xc2].includes(marker)){
      if(length<8)break;const height=buffer.readUInt16BE(offset+3),width=buffer.readUInt16BE(offset+5);
      if(width<16||height<16||width>1500||height>1500)throw new RecipeError('Размеры фото должны быть от 16 до 1500 px по каждой стороне.');
      return {width,height,bytes:buffer.length};
    }offset+=length;
  }
  throw new RecipeError('Не удалось прочитать размеры JPEG.');
}
