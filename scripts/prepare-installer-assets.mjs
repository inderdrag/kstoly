import {readFile,mkdir,writeFile} from 'node:fs/promises';
const root=new URL('../',import.meta.url);
const assets=JSON.parse(await readFile(new URL('scripts/assets.json',root),'utf8'));
const icon=Buffer.from(assets['public/icon-512.png']||'','base64');
if(icon.length<100||!icon.subarray(0,8).equals(Buffer.from([137,80,78,71,13,10,26,10])))throw Error('Missing or invalid approved app icon');
for(const path of ['desktop/icon.png','android/app/src/main/res/drawable/app_icon.png']){const target=new URL(path,root);await mkdir(new URL('./',target),{recursive:true});await writeFile(target,icon);}
console.log('Prepared approved app icons for Windows and Android.');
