import {nutritionEstimate} from './estimates.js';
export class RecipeError extends Error {}
export const MAX_RECIPES=10;
const mealTypes=new Set(['main','soup','side','breakfast','bake','salad','snack','dessert']);
const isSalad=r=>r.meal_type==='salad'||/(?:^|[^\p{L}])(?:салат|винегрет|оливье|табуле|коул[ -]?слоу|coleslaw|цезарь)(?=$|[^\p{L}])/iu.test(r.title);
const obj=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const text=(x,n=600)=>typeof x==='string'&&x.trim().length>0&&x.length<=n;
const number=(x,min,max)=>typeof x==='number'&&Number.isFinite(x)&&x>=min&&x<=max;
export function validateRecipe(data,{receipt=false,currency='RUB'}={}) {
  const fail=field=>{throw new RecipeError('Неверное поле: '+field+'.');};
  if(!obj(data))fail('recipe');
  if(!['ok','bad_photo','not_food','need_details'].includes(data.status))fail('status');
  if(data.status!=='ok'){
    if(!text(data.message,600))fail('message');
    return {status:data.status,message:data.message};
  }
  for(const [key,max] of [['title',150],['description',800]])if(!text(data[key],max))fail(key);
  if(!Number.isInteger(data.servings)||!number(data.servings,1,8))fail('servings');
  if(!number(data.prep_minutes,0,240))fail('prep_minutes');
  if(!number(data.cook_minutes,0,720))fail('cook_minutes');
  if(!Array.isArray(data.ingredients)||data.ingredients.length<1||data.ingredients.length>30)fail('ingredients');
  if(!Array.isArray(data.steps)||data.steps.length<1||data.steps.length>20)fail('steps');
  const ingredients=data.ingredients.map((i,index)=>{
    const key='ingredients['+index+']';
    if(!obj(i))fail(key);
    if(!text(i.name,100))fail(key+'.name');
    if(!number(i.amount,.001,100000))fail(key+'.amount');
    if(!['g','ml','piece'].includes(i.unit))fail(key+'.unit');
    let price=null;
    if(i.price!==null&&i.price!==undefined){
      const priceKey=key+'.price',p=i.price;
      if(!obj(p))fail(priceKey);
      if(!['estimate','receipt'].includes(p.source))fail(priceKey+'.source');
      if(!number(p.quantity,.001,100000))fail(priceKey+'.quantity');
      if(p.unit!==i.unit)fail(priceKey+'.unit');
      if(p.currency!==currency)fail(priceKey+'.currency');
      if(!number(p.low,0,1000000)||!number(p.high,0,1000000)||p.low>p.high)fail(priceKey+'.low/high');
      if(!text(p.note,400))fail(priceKey+'.note');
      if(p.source==='receipt'&&(!receipt||p.low!==p.high))fail(priceKey+'.receipt');
      price={source:p.source,quantity:p.quantity,unit:p.unit,currency:p.currency,low:p.low,high:p.high,note:p.note};
    }
    return {name:i.name,amount:i.amount,unit:i.unit,price};
  });
  const steps=data.steps.map((step,index)=>{if(!obj(step)||!text(step.title,100)||!text(step.body,2600))fail('steps['+index+']');const timer=Number.isInteger(step.timer_seconds)&&number(step.timer_seconds,1,21600)?{timer_seconds:step.timer_seconds,...(text(step.timer_hint,240)?{timer_hint:step.timer_hint}:{})}:{};const indexes=Array.isArray(step.ingredient_indexes)&&step.ingredient_indexes.length<=30&&step.ingredient_indexes.every(n=>Number.isInteger(n)&&n>=0&&n<ingredients.length)?{ingredient_indexes:[...new Set(step.ingredient_indexes)]}:{};return {title:step.title,body:step.body,...timer,...indexes};});
  const warnings=data.warnings===undefined?[]:data.warnings;
  if(!Array.isArray(warnings)||warnings.length>12||warnings.some(x=>!text(x,400)))fail('warnings');
  if(data.image_prompt!==undefined&&(!text(data.image_prompt,1000)||data.image_prompt.trim().length<40||!/[A-Za-z]/.test(data.image_prompt)||/[А-Яа-яЁё<>]/.test(data.image_prompt)))fail('image_prompt');
  if(data.meal_type!==undefined&&!mealTypes.has(data.meal_type))fail('meal_type');
  // Nutrition is optional for old recipes. Discard invalid estimates instead of retrying a paid model call.
  const n=data.nutrition;
  const nutrition=nutritionEstimate(n,data.servings);
  return {status:'ok',...(data.meal_type?{meal_type:data.meal_type}:{}),...(nutrition?{nutrition}:{}),...(Array.isArray(data.equipment)&&data.equipment.length<=6&&data.equipment.every(x=>['hob','oven','microwave','kettle','none'].includes(x))?{equipment:[...new Set(data.equipment)]}:{}),...(data.image_prompt?{image_prompt:data.image_prompt.trim()}:{}),title:data.title,description:data.description,servings:data.servings,prep_minutes:data.prep_minutes,cook_minutes:data.cook_minutes,ingredients,steps,warnings};
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

export function parseRecipeSelection(raw,options){
  if(typeof raw!=='string'||raw.length>240000)throw new RecipeError('Пустой или слишком большой ответ модели.');
  let source=raw.trim();if(source.startsWith('```'))source=source.replace(/^```(?:json)?\s*/,'').replace(/\s*```$/,'');
  let value;try{value=JSON.parse(source)}catch{throw new RecipeError('Модель вернула невалидный JSON.')}
  if(value?.status!=='ok')return validateRecipe(value,options);
  if(!Array.isArray(value.recipes)||value.recipes.length<1||value.recipes.length>MAX_RECIPES)throw new RecipeError('Ожидаются от 1 до 10 рецептов на выбор.');
  const recipes=value.recipes.map((r,index)=>{try{const recipe=validateRecipe(obj(r)&&r.status===undefined?{...r,status:'ok'}:r,options);if(recipe.status!=='ok')throw new RecipeError('В списке должен быть полный рецепт.');return recipe}catch(e){if(e instanceof RecipeError)throw new RecipeError('recipes['+index+']: '+e.message);throw e}});
  if(new Set(recipes.map(r=>r.title.trim().toLocaleLowerCase('ru'))).size!==recipes.length)throw new RecipeError('Названия вариантов должны различаться.');
  // Old saved single recipes stay compatible; selection balance is checked only for 3+ options.
  const saladLimit=recipes.length>=6?2:1;
  if(recipes.length>=3&&recipes.filter(isSalad).length>saladLimit)throw new RecipeError('В подборе слишком много салатов. Для 3–5 блюд допустим 1 салат, для 6–10 — не больше 2. Нужны другие способы приготовления, а не новые названия салатов.');
  const preferences=options?.preferences||[];
  if(preferences.includes('no_salads')&&recipes.some(isSalad))throw new RecipeError('Выбрано Без салатов. Замените салаты другими блюдами.');
  if(preferences.includes('quick')&&recipes.some(r=>r.prep_minutes+r.cook_minutes>20))throw new RecipeError('Выбрано До 20 минут, с учётом подготовки.');
  if(preferences.includes('no_oven')&&recipes.some(r=>r.equipment?.includes('oven')||/духовк|разогрейте печь|выпекайте|запекайте/iu.test(r.steps.map(s=>s.body).join(' '))))throw new RecipeError('Выбрано Без духовки. Предложите приготовление на плите или без нагрева.');
  recipes.sort((a,b)=>Number(isSalad(a))-Number(isSalad(b)));
  return {status:'ok',recipes};
}
