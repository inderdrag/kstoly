// Curated local photographs only. Model-supplied URLs are never loaded.
(()=>{
 const library=Object.freeze({
  pasta:Object.freeze({id:'pasta',src:'/photos/pesto-pasta.jpg',alt:'Паста с зеленью и томатами — пример подачи',author:'Eaters Collective',source:'https://unsplash.com/photos/12eHC6FxPyg',license:'CC0 1.0',licenseUrl:'https://creativecommons.org/publicdomain/zero/1.0/',reference:'https://commons.wikimedia.org/wiki/File:Pesto_Pasta_(Unsplash).jpg'}),
  salad:Object.freeze({id:'salad',src:'/photos/vegetable-salad.jpg',alt:'Овощной салат — пример подачи',author:'Anna Pelzer',source:'https://unsplash.com/photos/IGfIGP5ONV0',license:'Unsplash License',licenseUrl:'https://unsplash.com/license'})
 });
 function select(recipe){
  if(!recipe||typeof recipe.title!=='string')return null;
  const title=recipe.title.toLocaleLowerCase('ru');
  const names=(recipe.ingredients||[]).map(i=>String(i.name||'').toLocaleLowerCase('ru')).join(' ');
  const all=title+' '+names;
  // Avoid misleading meat/fish/dessert/creamy illustrations and unrelated dishes.
  if(/куриц|курин|индей|говяд|свин|ветчин|бекон|колбас|сосиск|лосос|тун[её]ц|рыб|кревет|краб|крабов|майонез|сливк|сметан/.test(all))return null;
  if(/паст|макарон|фарфалл|пенне/.test(title)&&/томат|помидор/.test(all)&&/шпинат|песто|базилик|зелень|руккол/.test(all))return library.pasta;
  if(/салат/.test(title)&&!/оливье|мимоз|сел[её]д|фрукт|св[её]кл|винегрет|т[её]пл|жарен|вар[её]н|картофел/.test(all)&&/томат|помидор|авокадо|лист.*салат|нут/.test(all))return library.salad;
  return null;
 }
 globalThis.OlivaPhotos=Object.freeze({library,select});
})();
