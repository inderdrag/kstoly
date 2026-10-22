'use strict';
const ORIGIN='https://oliva-photo-helper.onrender.com';
function allowedPage(value){try{const u=new URL(value);return u.origin===ORIGIN&&!u.username&&!u.password}catch{return false}}
function allowPermission(webContents,permission,details={}){
 if(!webContents||webContents.isDestroyed()||!allowedPage(webContents.getURL()))return false;
 const source=details.requestingUrl||details.requestingOrigin||details.securityOrigin;
 if(source&&!allowedPage(source))return false;
 if(permission==='clipboard-sanitized-write')return true;
 const types=Array.isArray(details.mediaTypes)?details.mediaTypes:details.mediaType?[details.mediaType]:[];
 return permission==='media'&&types.length>0&&types.every(type=>type==='video');
}
module.exports={ORIGIN,allowedPage,allowPermission};
