// Formatting-only recovery. Never evaluate code, invent fields, close a cut-off
// document or bypass the recipe validators. Provider bodies are never logged.
export function parseModelJson(raw,maxLength=240000){
 if(typeof raw!=='string'||!raw.trim()||raw.length>maxLength)throw new SyntaxError('Invalid model document');
 let source=raw.trim().replace(/^\uFEFF/,'');
 if(source.startsWith('```')){
  const fence=/^```(?:json)?[ \t]*\r?\n([\s\S]*?)\s*```$/i.exec(source);
  if(!fence)throw new SyntaxError('Incomplete code fence');
  source=fence[1].trim();
 }
 try{return JSON.parse(source);}catch{}
 // A short plain-language preamble is allowed, not a second JSON/code block.
 const start=source.indexOf('{');
 if(start<0||start>500||/[\[\]`]/.test(source.slice(0,start)))throw new SyntaxError('Expected object');
 const stack=[];let quoted=false,escaped=false,end=-1,out='';
 for(let i=start;i<source.length;i++){
  const c=source[i];
  if(quoted){
   if(escaped){out+=c;escaped=false;continue;}
   if(c==='\\'){out+=c;escaped=true;continue;}
   if(c==='"'){quoted=false;out+=c;continue;}
   // JSON cannot contain literal newlines/tabs in strings; preserve their value.
   out+=c.charCodeAt(0)<32?JSON.stringify(c).slice(1,-1):c;continue;
  }
  if(c==='"'){quoted=true;out+=c;continue;}
  if(c==='{'||c==='[')stack.push(c==='{'?'}':']');
  else if(c==='}'||c===']'){
   if(stack.pop()!==c)throw new SyntaxError('Mismatched bracket');
  }
  if(c===','){
   let next=i+1;while(next<source.length&&/\s/.test(source[next]))next++;
   if(source[next]==='}'||source[next]===']')continue;
  }
  out+=c;
  if(stack.length===0){end=i+1;break;}
 }
 if(end<0||quoted||escaped||source.slice(end).trim())throw new SyntaxError('Incomplete or ambiguous document');
 return JSON.parse(out);
}
