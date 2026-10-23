import {readFileSync,writeFileSync,mkdirSync} from 'node:fs';
import {dirname,resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../',import.meta.url));
const assets=JSON.parse(readFileSync(new URL('./assets.json',import.meta.url),'utf8'));
for(const name of Object.keys(assets)){
 if(!['public/icon-192.png','public/icon-512.png','test/fixture.jpg','public/photos/pesto-pasta.jpg','public/photos/vegetable-salad.jpg'].includes(name))throw Error('Unknown bundled asset');
 const target=resolve(root,name);
 mkdirSync(dirname(target),{recursive:true});
 writeFileSync(target,Buffer.from(assets[name],'base64'));
}
console.log('К столу: local image assets ready.');
