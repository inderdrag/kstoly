import {jpegInfo,RecipeError} from './recipe.js';
export const PREFERENCES=Object.freeze({hearty:'Сытное: отдавай предпочтение полноценным блюдам с белком и гарниром из видимых продуктов.',quick:'До 20 минут суммарно на подготовку и приготовление. Не сокращай реальное время ради фильтра.',no_salads:'Без салатов: не включай салаты, даже переименованные.',low_cal:'Мало калорий: ориентир не более 150 ккал на 100 г готового блюда. Учитывай масло, соусы и готовый выход. Не уменьшай порции ради цифры, не выдавай неизвестную калорийность за низкую.',no_oven:'Без духовки: не используй духовку, запекание или гриль духовки. Плита допустима.'});
export function selectionInput(data){
 if(data.images!==undefined&&data.image!==undefined)throw new RecipeError('Передайте images либо image, не оба поля.');
 const images=data.images===undefined?[data.image]:data.images;
 if(!Array.isArray(images)||images.length<1||images.length>4)throw new RecipeError('Нужно от 1 до 4 фото продуктов.');
 images.forEach(jpegInfo);if(data.receipt)jpegInfo(data.receipt);
 const preferences=data.preferences===undefined?[]:data.preferences;
 if(!Array.isArray(preferences)||preferences.length>Object.keys(PREFERENCES).length||preferences.some(x=>!Object.hasOwn(PREFERENCES,x))||new Set(preferences).size!==preferences.length)throw new RecipeError('Неизвестное пожелание к подбору.');
 return {images,preferences,preferenceText:preferences.map(x=>PREFERENCES[x]).join(' ')};
}
