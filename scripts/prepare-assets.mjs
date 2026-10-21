import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const assets=JSON.parse(readFileSync(new URL('./assets.json',import.meta.url),'utf8'));
for(const name of ['public/icon-192.png','public/icon-512.png','test/fixture.jpg']){
 const target=resolve(root,name);
 mkdirSync(dirname(target),{recursive:true});
 writeFileSync(target,Buffer.from(assets[name],'base64'));
}
console.log('Oliva: local image assets ready.');
