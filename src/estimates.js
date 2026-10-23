// Deliberately coarse reference ranges, not live retail prices or a price feed.
// Basis: RUB per kg/litre, except eggs (piece). Other currencies require a model/receipt estimate.
const references=[
 [/макарон|^паста$|паста сух|спагетти/, 'g',90,260],[/рис|греч|овсян|круп/, 'g',80,240],[/мук/, 'g',50,120],
 [/картоф/, 'g',35,120],[/морков|св[её]кл/, 'g',35,110],[/лук репчат|луковиц/, 'g',30,100],
 [/томат|помидор/, 'g',120,400],[/огур/, 'g',100,350],[/капуст/, 'g',35,150],[/кабач/, 'g',60,230],
 [/перец болгар|сладкий перец/, 'g',150,450],[/авокад/, 'piece',60,180],[/яблок/, 'g',70,220],[/банан/, 'g',100,230],
 [/куриц|курин/, 'g',180,500],[/индей/, 'g',350,850],[/говядин/, 'g',500,1200],[/свинин/, 'g',280,650],
 [/рыб|минтай|треск/, 'g',220,900],[/яйц/, 'piece',8,20],[/молок/, 'ml',70,150],[/сливки/, 'ml',250,650],
 [/сметан/, 'g',200,500],[/творожн.*сыр/, 'g',550,1400],[/творог/, 'g',300,800],[/сыр/, 'g',600,1600],
 [/хлеб|батон|тост/, 'g',90,260],[/масло слив/, 'g',700,1500],[/масло.*(раст|подсолн)|растительное масло/, 'ml',90,220],
 [/оливков.*масл|масло олив/, 'ml',500,1600],[/сахар/, 'g',50,110],[/соль/, 'g',15,70],[/шампиньон/, 'g',180,500]
];
export function referencePrice(i,currency){if(currency!=='RUB')return null;const row=references.find(([pattern,unit])=>unit===i.unit&&pattern.test(i.name.toLocaleLowerCase('ru')));return row?{source:'estimate',quantity:i.unit==='piece'?1:1000,unit:i.unit,currency,low:row[2],high:row[3],note:'Широкий справочный ориентир, не актуальная цена магазина. Регион и сезон могут заметно изменить стоимость.'}:null;}
export function automaticEstimates(recipe,currency){if(recipe.status!=='ok')return recipe;return {...recipe,ingredients:recipe.ingredients.map(i=>({...i,price:i.price||referencePrice(i,currency)}))};}
export function nutritionEstimate(n,servings){if(!n||n.source!=='estimate')return null;const total=n.total_kcal,weight=n.cooked_weight_g;const valid=Number.isFinite(total)&&total>0&&total<=100000&&Number.isFinite(weight)&&weight>=10&&weight<=100000;
 if(valid){const canonicalTotal=Math.max(1,Math.round(total)),canonicalWeight=Math.round(weight);const value=Math.round(canonicalTotal/canonicalWeight*100);if(value>0&&value<=2000)return {source:'estimate',total_kcal:canonicalTotal,cooked_weight_g:canonicalWeight,kcal_per_100g:value,kcal_per_serving:Math.round(canonicalTotal/servings)};}
 // Backward compatibility only. Never relabel old per-serving numbers as per-100g.
 return Number.isFinite(n.kcal_per_serving)&&n.kcal_per_serving>=1&&n.kcal_per_serving<=5000?{source:'estimate',kcal_per_serving:Math.max(5,Math.round(n.kcal_per_serving/5)*5)}:null;
}
