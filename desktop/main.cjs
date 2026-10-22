'use strict';
const {app,BrowserWindow,session,dialog}=require('electron');
const {ORIGIN,allowedPage,allowPermission}=require('./policy.cjs');
let main;
async function openApp(){
 const store=session.fromPartition('persist:k-stolu');
 store.setPermissionRequestHandler((wc,p,callback,details)=>callback(allowPermission(wc,p,details)));
 store.setPermissionCheckHandler((wc,p,origin,details)=>allowPermission(wc,p,{...details,requestingOrigin:origin}));
 main=new BrowserWindow({width:1120,height:840,minWidth:400,minHeight:620,title:'К столу',icon:require('node:path').join(__dirname,'icon.png'),backgroundColor:'#f8f7f0',autoHideMenuBar:true,webPreferences:{partition:'persist:k-stolu',nodeIntegration:false,contextIsolation:true,sandbox:true,webSecurity:true,allowRunningInsecureContent:false}});
 main.webContents.setWindowOpenHandler(()=>({action:'deny'}));
 main.webContents.on('will-navigate',(event,url)=>{if(!allowedPage(url))event.preventDefault()});
 main.webContents.on('will-redirect',(event,url)=>{if(!allowedPage(url))event.preventDefault()});
 main.webContents.on('did-fail-load',async(_event,code,_description,url,isMainFrame)=>{
  if(!isMainFrame||code===-3||!allowedPage(url))return;
  const {response}=await dialog.showMessageBox(main,{type:'warning',title:'К столу',message:'Не удалось открыть приложение',detail:'Проверьте интернет. Бесплатный сервер Render после простоя может запускаться около минуты. Повтор открытия не отправляет фото и не создаёт рецепты.',buttons:['Повторить','Закрыть'],defaultId:0,cancelId:1});
  if(response===0&&!main.isDestroyed())main.loadURL(ORIGIN).catch(()=>{});else if(!main.isDestroyed())main.close();
 });
 // No preload bridge, filesystem access or external navigation is exposed to the website.
 main.loadURL(ORIGIN).catch(()=>{});
}
if(!app.requestSingleInstanceLock())app.quit();else{
 app.on('second-instance',()=>{if(main){if(main.isMinimized())main.restore();main.focus()}});
 app.whenReady().then(openApp);
 app.on('window-all-closed',()=>{if(process.platform!=='darwin')app.quit()});
 app.on('activate',()=>{if(BrowserWindow.getAllWindows().length===0)openApp()});
}
