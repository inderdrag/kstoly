// Only for generated answers, never for user data, receipt input or saved recipes.
// Optional cost estimates must not invalidate otherwise valid cooking instructions.
const object=x=>x!==null&&typeof x==='object'&&!Array.isArray(x);
const number=(x,min,max)=>typeof x==='number'&&Number.isFinite(x)&&x>=min&&x<=max;
const units=new Map([
 ['g',['g',1]],['г',['g',1]],['гр',['g',1]],['грамм',['g',1]],['граммы',['g',1]],['граммов',['g',1]],
 ['kg',['g',1000]],['кг',['g',1000]],
 ['ml',['ml',1]],['мл',['ml',1]],['миллилитр',['ml',1]],['миллилитров',['ml',1]],
 ['l',['ml',1000]],['л',['ml',1000]],['литр',['ml',1000]],['литров',['ml',1000]],
 ['piece',['piece',1]],['pieces',['piece',1]],['pc',['piece',1]],['pcs',['piece',1]],['шт',['piece',1]],['штука',['piece',1]],['штук',['piece',1]]
]);
export function generatedPrice(price,ingredientUnit,{currency='RUB',receipt=false}={}){
 if(!object(price)||!['estimate','receipt'].includes(price.source))return null;
 if(typeof price.unit!=='string'||!['g','ml','piece'].includes(ingredientUnit))return null;
 const unit=units.get(price.unit.trim().toLowerCase().replace(/\.$/u,''));
 // Never infer package size, grams per piece, density or currency exchange.
 if(!unit||unit[0]!==ingredientUnit||!number(price.quantity,.001,100000))return null;
 const quantity=price.quantity*unit[1];
 if(!number(quantity,.001,100000)||price.currency!==currency||!number(price.low,0,1000000)||!number(price.high,0,1000000)||price.low>price.high)return null;
 if(typeof price.note!=='string'||!price.note.trim()||price.note.length>400)return null;
 if(price.source==='receipt'&&(!receipt||price.low!==price.high))return null;
 return {source:price.source,quantity,unit:ingredientUnit,currency,low:price.low,high:price.high,note:price.note};
}
export function generatedRecipePrices(recipe,options){
 if(!object(recipe)||!Array.isArray(recipe.ingredients))return recipe;
 let discarded=false;
 const ingredients=recipe.ingredients.map(i=>{
  if(!object(i)||i.price==null)return i;
  const price=generatedPrice(i.price,i.unit,options);if(!price)discarded=true;
  return {...i,price};
 });
 const warnings=recipe.warnings===undefined?[]:recipe.warnings;
 const warning='Часть цен модели не прошла проверку единиц или формата. Вместо них используется справочная оценка, где она доступна; остальные цены неизвестны.';
 // Do not mask malformed warnings or remove any existing safety warning.
 return {...recipe,ingredients,...(discarded&&Array.isArray(warnings)&&warnings.length<12?{warnings:[...warnings,warning]}:{})};
}
