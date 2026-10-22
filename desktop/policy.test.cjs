const test=require('node:test'),assert=require('node:assert/strict');
const {ORIGIN,allowedPage,allowPermission}=require('./policy.cjs');
const wc={isDestroyed:()=>false,getURL:()=>ORIGIN+'/'};
test('only the exact HTTPS application origin can navigate',()=>{
 assert.equal(allowedPage(ORIGIN+'/api/status'),true);
 for(const x of ['http://oliva-photo-helper.onrender.com','https://oliva-photo-helper.onrender.com.evil.test','file:///etc/passwd','javascript:alert(1)','https://user:password@oliva-photo-helper.onrender.com'])assert.equal(allowedPage(x),false);
});
test('only video capture from the app may request media access',()=>{
 assert.equal(allowPermission(wc,'media',{mediaTypes:['video'],requestingUrl:ORIGIN}),true);
 for(const types of [[],['audio'],['audio','video']])assert.equal(allowPermission(wc,'media',{mediaTypes:types}),false);
 assert.equal(allowPermission(wc,'media',{mediaTypes:['video'],requestingUrl:'https://evil.test'}),false);
 assert.equal(allowPermission(wc,'media',{mediaType:'video',requestingOrigin:ORIGIN}),true);
 assert.equal(allowPermission(wc,'media',{mediaType:'audio',requestingOrigin:ORIGIN}),false);
 assert.equal(allowPermission(wc,'geolocation'),false);assert.equal(allowPermission(null,'media',{mediaTypes:['video']}),false);
});
