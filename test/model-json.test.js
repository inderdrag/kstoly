import test from 'node:test';
import assert from 'node:assert/strict';
import {parseModelJson} from '../src/model-json.js';
import {parseRecipeSelection} from '../src/recipe.js';
import {parseInventory} from '../src/pantry.js';
const recipe={status:'ok',title:'Рис',description:'Рис с томатами',servings:2,prep_minutes:5,cook_minutes:15,ingredients:[{name:'Рис',amount:100,unit:'g',price:null}],steps:[{title:'Сварите',body:'Варите по инструкции.\nПроверьте готовность.'}],warnings:[]};
const document=JSON.stringify({status:'ok',recipes:[recipe]});
test('JSON: valid document keeps strings, escapes, quantities and timers unchanged',()=>assert.deepEqual(parseModelJson(document),JSON.parse(document)));
test('JSON: uppercase fence, BOM and surrounding whitespace',()=>assert.deepEqual(parseModelJson('\uFEFF```JSON\n'+document+'\n```'),JSON.parse(document)));
test('JSON: trailing commas outside strings recover without paid call',()=>assert.deepEqual(parseModelJson(document.replace(/\}\]\}$/,'},],}')),JSON.parse(document)));
test('JSON: literal linebreaks and tabs inside strings preserve text',()=>assert.deepEqual(parseModelJson('{"text":"one\ntwo\tthree\r"}'),{text:'one\ntwo\tthree\r'}));
test('JSON: short preamble accepted only with one complete document',()=>assert.deepEqual(parseModelJson('Вот рецепты:\n'+document),JSON.parse(document)));
test('JSON: commas, brackets and escaped quotes in instructions are not rewritten',()=>{const value={text:'Скажите "готово", затем }, ] и \\n'};assert.deepEqual(parseModelJson(JSON.stringify(value).replace(/}$/ ,',}')),value)});
test('JSON: no fabricated endings, missing values, executable code or ambiguous objects',()=>{for(const raw of [document.slice(0,-1),'{"status":"ok","recipes":[', '{"title":"cut off', '{"x":}', '{"x":1}{"x":2}', '{"x":1};process.exit()', '```json\n'+document, '```js\n'+document+'\n```'])assert.throws(()=>parseModelJson(raw),SyntaxError)});
test('JSON: incomplete recipe is not salvaged by closing its brackets',()=>assert.throws(()=>parseRecipeSelection(document.slice(0,-3))));
test('JSON: recovered formatting does not bypass ingredient, unit and preference validation',()=>{
 for(const field of ['amount','unit']){
  const bad=structuredClone(recipe);bad.ingredients[0][field]=field==='amount'?-1:'kg';
  assert.throws(()=>parseRecipeSelection(JSON.stringify({status:'ok',recipes:[bad]}).replace(/}$/ ,',}')));
 }
 const salad={...recipe,title:'Салат',meal_type:'salad'};
 const raw=JSON.stringify({status:'ok',recipes:[salad]}).replace(/}$/ ,',}');
 assert.throws(()=>parseRecipeSelection(raw,{preferences:['no_salads']}));
});
test('JSON: inventory uses the same bounded formatting recovery',()=>{assert.deepEqual(parseInventory('```JSON\n{"status":"ok","products":["Рис",],"receiptPrices":[],}\n```','RUB',false).products,['Рис']);assert.throws(()=>parseInventory('{"status":"ok","products":["Рис"]','RUB',false))});
test('JSON: oversized and non-string responses rejected',()=>{for(const raw of [null,{},'x'.repeat(240001),''])assert.throws(()=>parseModelJson(raw))});
