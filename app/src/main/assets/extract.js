(()=>{const stores={'BIG W':'https://www.bigw.com.au/collection/clearance',Kmart:'https://www.kmart.com.au/category/clearance/shop-all-clearance/',Officeworks:'https://www.officeworks.com.au/shop/officeworks/c/clearance'};
function product(raw,store){try{const u=new URL(raw,stores[store]);if(u.protocol!=='https:'||u.hostname!==new URL(stores[store]).hostname||!(/\/p\/|\/product\//.test(u.pathname)))return '';return u.origin+u.pathname}catch{return ''}}
function prices(text){const s=text.replace(/(\d)(?=originally|was|previously)/gi,'$1 ').replace(/\s+/g,' ');const old=/\b(?:originally|was|previously)\s*:?\s*\$\s*([\d,]+(?:\.\d{1,2})?)/i.exec(s);if(!old)return null;const before=s.slice(0,old.index);if(/\$[\d.,]+\s*[-–]\s*\$|\bfrom\s*\$/i.test(before))return null;const nums=[...before.matchAll(/\$\s*([\d,]+(?:\.\d{1,2})?)/g)].map(m=>Number(m[1].replaceAll(',','')));if(!nums.length||new Set(nums).size!==1)return null;const now=nums[0],was=Number(old[1].replaceAll(',',''));return now>0&&was>now?{now,was}:null}
function singlePrice(raw){
 const values=[...raw.matchAll(/\$\s*([\d,]+(?:\.\d{1,2})?)/g)].map(m=>Number(m[1].replaceAll(',','')));
 return values.length&&new Set(values).size===1&&values[0]>0?values[0]:null
}

const store=Object.keys(stores).find(k=>new URL(stores[k]).hostname===location.hostname);
if(!store||!location.pathname.toLowerCase().includes('clearance'))return {rows:[]};
const text=(document.body?.innerText||'').slice(0,600);
if(/verify you are human|access denied|checking your browser/i.test(text))return {blocked:true,rows:[]};
 const rows=new Map();
 const grid=document.querySelector('main,[role="main"]')||document;
 for(const a of grid.querySelectorAll('a[href]')){
  const url=product(a.href,store);if(!url||rows.get(url)?.was)continue;
  const name=(a.getAttribute('aria-label')||a.innerText||a.querySelector('img')?.alt||'').split('$')[0].replace(/^clearance\s*/i,'').trim();
  if(name.length<4||name.length>220||/^image|^add to/i.test(name))continue;
  for(let el=a,level=0;el&&level<8;level++,el=el.parentElement){
   if([...el.querySelectorAll('a[href]')].some(x=>{const other=product(x.href,store);return other&&other!==url}))break;
   const raw=el.innerText||'',pair=prices(raw),now=pair?.now||singlePrice(raw);
   if(pair||now&&!rows.get(url)?.now||!rows.has(url))rows.set(url,{name,url,now:now||null,was:pair?.was||null});
   if(pair)break;
  }
 }
window.scrollBy(0,Math.max(400,innerHeight*.85));return {rows:[...rows.values()]}})();