const $=id=>document.getElementById(id);
const DB={
  open(){return this._p||(this._p=new Promise((ok,no)=>{const r=indexedDB.open('m3u-viewer',1);r.onupgradeneeded=()=>r.result.createObjectStore('kv');r.onsuccess=()=>ok(r.result);r.onerror=()=>{this._p=null;no(r.error)}}))},
  async get(k){const d=await this.open();return new Promise(ok=>{const q=d.transaction('kv').objectStore('kv').get(k);q.onsuccess=()=>ok(q.result);q.onerror=()=>ok(null)})},
  async set(k,v){const d=await this.open();return new Promise((ok,no)=>{const t=d.transaction('kv','readwrite');t.objectStore('kv').put(v,k);t.oncomplete=ok;t.onerror=()=>no(t.error)})},
  async del(k){const d=await this.open();return new Promise(ok=>{const t=d.transaction('kv','readwrite');t.objectStore('kv').delete(k);t.oncomplete=ok;t.onerror=ok})}
};

// ================== القنوات المدمجة (يمكنك تعديلها هنا) ==================
const BUILT_IN_M3U = `#EXTM3U
#EXTINF:-1 tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/1/1b/EPTV_logo.svg/200px-EPTV_logo.svg.png" group-title="قنوات جزائرية",الجزائرية 1
https://example.com/algerie1.m3u8
#EXTINF:-1 tvg-logo="https://upload.wikimedia.org/wikipedia/commons/thumb/9/9c/Logo_El_Heddaf_TV.png/200px-Logo_El_Heddaf_TV.png" group-title="قنوات رياضية",الهداف TV
https://example.com/elheddaf.m3u8
#EXTINF:-1 group-title="قنوات إخبارية",الجزيرة
https://example.com/aljazeera.m3u8
`;
// ==========================================================================

function parseM3U(text){
  const out=[],lines=text.replace(/^\uFEFF/,'').split(/\r?\n/);
  let meta=null;
  for(const raw of lines){
    const l=raw.trim();
    if(!l)continue;
    if(l.startsWith('#EXTINF')){
      const attrs={};
      l.replace(/([\w-]+)="([^"]*)"/g,(_,k,v)=>{attrs[k.toLowerCase()]=v});
      const i=l.lastIndexOf(',');
      meta={name:(i>-1?l.slice(i+1):'').trim()||attrs['tvg-name']||'',logo:attrs['tvg-logo']||'',group:attrs['group-title']||'',raw:l,extra:[]};
    }else if(l.startsWith('#EXTGRP:')&&meta){meta.group=meta.group||l.slice(8).trim();meta.extra.push(l)}
    else if(l.startsWith('#')){if(meta)meta.extra.push(l)}
    else{
      const m=meta||{name:'',logo:'',group:'',raw:'',extra:[]};
      if(!m.name)m.name=l.split('/').pop().split('?')[0]||l;
      m.url=l;out.push(m);meta=null;
    }
  }
  return out;
}

function detectKind(item){
  const g=(item.group||'').toLowerCase();
  const u=(item.url||'').toLowerCase();
  const n=(item.name||'').toLowerCase();
  if(/مسلسل|مسلسلات|series|show|season|episode|حلقة/.test(g)||/مسلسل|حلقة\s*\d|s\d{1,2}e\d{1,2}/.test(n))return 'series';
  if(/فيلم|أفلام|افلام|movie|movies|film|cinema|vod/.test(g))return 'movie';
  if(/\.(mp4|mkv|avi|mov|wmv|webm|flv)(\?|$)/.test(u)&&!/\.m3u8?(\?|$)/.test(u))return 'movie';
  if(/^فيلم|^movie|^film/.test(n))return 'movie';
  if(/radio|راديو/.test(g)||/\.(mp3|aac|ogg|m4a)(\?|$)/.test(u))return 'radio';
  return 'live';
}
const KIND_ICON={live:'📺',movie:'🎬',series:'🎞️',radio:'📻'};
const KIND_NAME={live:'قنوات',movie:'أفلام',series:'مسلسلات',radio:'راديو'};

const LS={get(k,d){try{const v=localStorage.getItem(k);return v==null?d:JSON.parse(v)}catch(e){return d}},set(k,v){try{localStorage.setItem(k,JSON.stringify(v))}catch(e){}},del(k){try{localStorage.removeItem(k)}catch(e){}}};
let lists=[],active='',items=[],shown=[],hls=null,mp=null,curUrl='',tok=0,mergeTarget='';
let favs=new Set(LS.get('m3u_favs',[])),recs=LS.get('m3u_recs',[]);
const FAV_LIST_ID='__fav_list__';
const FAV_LIST_NAME='⭐ المفضلة';

function ser(a){return '#EXTM3U\n'+a.map(i=>(i.raw?i.raw+'\n':'')+i.extra.join('\n')+(i.extra.length?'\n':'')+i.url).join('\n')+'\n'}
function merge(oldI,newI,strict){
  const nm=new Map(newI.map(i=>[i.url,i])),seen=new Set(),out=[];
  oldI.forEach(i=>{if(nm.has(i.url)){if(!seen.has(i.url)){out.push(nm.get(i.url));seen.add(i.url)}}else if(!strict)out.push(i)});
  newI.forEach(i=>{if(!seen.has(i.url)){out.push(i);seen.add(i.url)}});
  return out;
}
function setMsg(t,err){const m=$('msg');m.textContent=t||'';m.className=err?'err':'';m.style.color = err ? '#c4452d' : '#fff';}
function saveLists(){return DB.set('lists',{lists,active}).catch(()=>{})}
async function getText(u){const r=await fetch(u,{cache:'no-store'});if(!r.ok)throw 0;return await r.text()}

async function syncFavList(){
  const storedMeta=LS.get('m3u_fav_meta',{});
  const allFavItems=[];
  favs.forEach(u=>{
    const fromCurrent=items.find(i=>i.url===u);
    if(fromCurrent){
      allFavItems.push(fromCurrent);
      storedMeta[u]={name:fromCurrent.name,logo:fromCurrent.logo,group:fromCurrent.group,raw:fromCurrent.raw,extra:fromCurrent.extra||[]};
    }else if(storedMeta[u]){
      allFavItems.push({...storedMeta[u],url:u});
    }else{
      allFavItems.push({name:u.split('/').pop()||u,logo:'',group:'المفضلة',raw:'',extra:[],url:u});
    }
  });
  LS.set('m3u_fav_meta',storedMeta);
  const text=ser(allFavItems);
  let favList=lists.find(l=>l.id===FAV_LIST_ID);
  if(favList){
    favList.text=text;favList.n=allFavItems.length;favList.upd=Date.now();
  }else if(allFavItems.length){
    favList={id:FAV_LIST_ID,name:FAV_LIST_NAME,text,url:'',n:allFavItems.length,upd:Date.now(),isFav:true};
    lists.push(favList);
  }
  await saveLists();
  fillLists();
}

const MODES=['auto','light','dark'],MTXT={auto:'🌓 تلقائي',light:'☀ نهاري',dark:'🌙 ليلي'};
let theme=LS.get('m3u_theme','auto');
function applyTheme(){if(theme==='auto')document.documentElement.removeAttribute('data-theme');else document.documentElement.setAttribute('data-theme',theme);$('theme').textContent=MTXT[theme]}
function cycleTheme(){theme=MODES[(MODES.indexOf(theme)+1)%3];LS.set('m3u_theme',theme);applyTheme()}
$('theme').onclick=cycleTheme;applyTheme();

function fillLists(){
  const visible=lists.filter(l=>l.id!==FAV_LIST_ID||l.n>0);
  const s=$('lists');s.classList.toggle('hide',!visible.length);
  s.innerHTML=visible.map(l=>'<option value="'+l.id+'"'+(l.id===active?' selected':'')+'>'+esc(l.name)+'</option>').join('');
}
function fillGroups(keep){
  const prev=$('grp').value,g=[...new Set(items.map(i=>i.group||'بدون مجموعة'))];
  const availCount=items.filter(i=>i.url&&i.url.trim()).length;
  const kinds={live:0,movie:0,series:0,radio:0};
  items.forEach(it=>{kinds[detectKind(it)]++});
  let kindOpts='';
  ['movie','series','live','radio'].forEach(k=>{if(kinds[k])kindOpts+='<option value="__k_'+k+'">'+KIND_ICON[k]+' '+KIND_NAME[k]+' ('+kinds[k]+')</option>'});
  $('grp').innerHTML='<option value="">كل المجموعات ('+items.length+')</option><option value="__fav">★ المفضلة</option><option value="__rec">🕘 آخر ما شاهدت</option><option value="__avail">✅ الروابط المتاحة ('+availCount+')</option>'
    +(kindOpts?'<optgroup label="النوع">'+kindOpts+'</optgroup>':'')
    +'<optgroup label="المجموعات">'+g.map(x=>'<option value="'+esc(x)+'">'+esc(x)+'</option>').join('')+'</optgroup>';
  if(keep&&[...$('grp').options].some(o=>o.value===prev))$('grp').value=prev;
}
function showWelcome(){items=[];shown=[];$('side').classList.add('hide');$('player').classList.add('hide');$('load').classList.remove('hide')}
function openList(id){
  const l=lists.find(x=>x.id===id);if(!l)return;
  const same=(id===active);active=id;items=parseM3U(l.text);fillGroups(same);
  if(!curUrl&&recs.length&&items.some(i=>i.url===recs[0]))curUrl=recs[0];
  $('load').classList.add('hide');$('side').classList.remove('hide');$('player').classList.remove('hide');
  $('upd').style.display=(active===FAV_LIST_ID||!l.url)?'none':'';
  fillLists();render();saveLists();
}
async function addList(text,name,url){
  const n=parseM3U(text);
  if(!n.length){setMsg('لم يتم العثور على قنوات. تأكد أن الملف بصيغة M3U وغير مشفّر.',true);return false}
  let l=url?lists.find(x=>x.url===url):null;
  if(l){const out=merge(parseM3U(l.text),n,false);l.text=ser(out);l.n=out.length;l.upd=Date.now()}
  else{l={id:'l'+Date.now().toString(36)+Math.random().toString(36).slice(2,5),name:name||('قائمة '+(lists.filter(x=>x.id!==FAV_LIST_ID).length+1)),text:ser(n),url:url||'',n:n.length,upd:Date.now()};lists.push(l)}
  openList(l.id);mgr();setMsg('تمت إضافة «'+l.name+'» ('+l.n+' قناة).');return true;
}

// دالة لجلب قنوات Xtream Codes (Extreme)
async function loadXtreamCodes(host, username, password, name) {
  try {
    setMsg('جارٍ الاتصال بسيرفر Extreme...');
    host = host.replace(/\/$/, ""); // إزالة السلاش الأخير إن وجد
    
    const liveUrl = `${host}/player_api.php?username=${username}&password=${password}&action=get_live_streams`;
    const vodUrl = `${host}/player_api.php?username=${username}&password=${password}&action=get_vod_streams`;

    const [liveRes, vodRes] = await Promise.all([
      fetch(liveUrl).catch(()=>null),
      fetch(vodUrl).catch(()=>null)
    ]);

    let m3uText = '#EXTM3U\n';
    let added = 0;

    if (liveRes && liveRes.ok) {
      const liveData = await liveRes.json();
      if(Array.isArray(liveData)){
        liveData.forEach(ch => {
          m3uText += `#EXTINF:-1 tvg-id="${ch.epg_channel_id||''}" tvg-name="${ch.name}" tvg-logo="${ch.stream_icon||''}" group-title="قنوات مباشرة",${ch.name}\n`;
          m3uText += `${host}/live/${username}/${password}/${ch.stream_id}.m3u8\n`;
          added++;
        });
      }
    }

    if (vodRes && vodRes.ok) {
      const vodData = await vodRes.json();
      if(Array.isArray(vodData)){
        vodData.forEach(movie => {
          m3uText += `#EXTINF:-1 tvg-name="${movie.name}" tvg-logo="${movie.stream_icon||''}" group-title="أفلام",${movie.name}\n`;
          m3uText += `${host}/movie/${username}/${password}/${movie.stream_id}.${movie.container_extension||'mp4'}\n`;
          added++;
        });
      }
    }

    if(added === 0) throw new Error("لم يتم العثور على قنوات في هذا السيرفر.");
    
    await addList(m3uText, name || 'سيرفر Extreme', null);
    $('dlg').close();
    setMsg('تم إضافة سيرفر Extreme بنجاح ('+added+' عنصر).');
  } catch (e) {
    setMsg('فشل الاتصال بسيرفر Xtream. تأكد من صحة البيانات والرابط.', true);
  }
}

async function updateList(id){
  if(id===FAV_LIST_ID){setMsg('قائمة المفضلة لا تُحدَّث من رابط.',true);return}
  const l=lists.find(x=>x.id===id);
  if(!l){setMsg('لا توجد قائمة لتحديثها.',true);return}
  if(!l.url){setMsg('«'+l.name+'» بلا رابط تحديث.',true);return}
  setMsg('جارٍ تحديث «'+l.name+'»...');
  try{
    const n=parseM3U(await getText(l.url));if(!n.length)throw 1;
    const old=parseM3U(l.text),had=new Set(old.map(i=>i.url));
    const out=merge(old,n,false);
    l.text=ser(out);l.n=out.length;l.upd=Date.now();await saveLists();
    if(id===active)openList(id);mgr();
    setMsg('تم التحديث: '+n.filter(i=>!had.has(i.url)).length+' قناة جديدة، المجموع '+out.length+'.');
  }catch(e){setMsg('تعذر التحديث من الرابط (قد يمنعه الموقع أو يكون الملف مشفّراً).',true)}
}
async function deleteList(id){
  if(id===FAV_LIST_ID){setMsg('لا يمكن حذف قائمة المفضلة.',true);return}
  lists=lists.filter(x=>x.id!==id);
  if(active===id){tok++;stopAll();active=lists.find(l=>l.id!==FAV_LIST_ID)?.id||'';if(active)openList(active);else showWelcome()}
  fillLists();mgr();await saveLists();
}
function mgr(){
  const b=$('mgr');b.textContent='';
  const normalLists=lists.filter(l=>l.id!==FAV_LIST_ID);
  if(!normalLists.length){b.innerHTML='<p class="small">لا توجد قوائم بعد. أضف قائمة من الأعلى.</p>';return}
  normalLists.forEach(l=>{
    const d=document.createElement('div');d.className='lrow'+(l.id===active?' cur':'');
    d.innerHTML='<input class="ln" value="'+esc(l.name)+'"><input class="lu" type="url" placeholder="رابط التحديث (اختياري)" value="'+esc(l.url||'')+'"><div class="lb"><button class="alt o">فتح</button><button class="alt m">دمج ملف</button><button class="alt u">🔄 تحديث</button><button class="alt x">حذف</button></div><small>'+(l.n||0)+' قناة'+(l.upd?' · '+new Date(l.upd).toLocaleDateString('ar'):'')+'</small>';
    const q=s=>d.querySelector(s);
    q('.ln').onchange=e=>{l.name=e.target.value.trim()||l.name;fillLists();saveLists()};
    q('.lu').onchange=e=>{l.url=e.target.value.trim();saveLists()};
    q('.o').onclick=()=>{openList(l.id);$('dlg').close()};
    q('.m').onclick=()=>{mergeTarget=l.id;$('mfile').click()};
    q('.u').onclick=()=>updateList(l.id);
    let armed=0;const x=q('.x');
    x.onclick=()=>{if(armed){deleteList(l.id);return}armed=1;x.textContent='تأكيد؟';setTimeout(()=>{armed=0;x.textContent='حذف'},3000)};
    b.appendChild(d);
  });
}

function esc(s){return String(s).replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]))}
function render(){
  const q=$('q').value.trim().toLowerCase(),g=$('grp').value;
  let base=items;
  if(g==='__fav')base=items.filter(i=>favs.has(i.url));
  else if(g==='__rec')base=recs.map(u=>items.find(i=>i.url===u)).filter(Boolean);
  else if(g==='__avail')base=items.filter(i=>i.url&&i.url.trim());
  else if(g.startsWith('__k_')){const k=g.slice(4);base=items.filter(i=>detectKind(i)===k)}
  else if(g)base=items.filter(i=>(i.group||'بدون مجموعة')===g);
  shown=base.filter(i=>!q||i.name.toLowerCase().includes(q));
  $('count').textContent='يعرض '+shown.length+' من '+items.length;
  const box=$('list');box.textContent='';
  const frag=document.createDocumentFragment();
  shown.slice(0,1500).forEach(it=>{
    const pic=it.logo?'<img loading="lazy" alt="" src="'+esc(it.logo)+'" onerror="this.outerHTML=\'<span class=ph>'+esc((it.name[0]||'?').replace(/[<>&"']/g,''))+'</span>\'">':'<span class="ph">'+esc(it.name[0]||'?')+'</span>';
    const kind=detectKind(it);
    const w=document.createElement('div');w.className='rw';
    const b=document.createElement('button');b.className='ch'+(it.url===curUrl?' on':'');b.type='button';b._it=it;
    b.innerHTML=pic+'<div><b>'+KIND_ICON[kind]+' '+esc(it.name)+'</b><small>'+esc(it.group||'بدون مجموعة')+'</small></div>';
    b.onclick=()=>play(it);
    const s=document.createElement('button');s.type='button';s.className='st'+(favs.has(it.url)?' on':'');s.textContent=favs.has(it.url)?'★':'☆';s._u=it.url;s.title='المفضلة';s.onclick=()=>toggleFav(it.url);
    w.append(b,s);frag.appendChild(w);
  });
  box.appendChild(frag);
  if(shown.length>1500){const d=document.createElement('div');d.className='count';d.textContent='تم عرض أول 1500 نتيجة. استخدم البحث لتضييق القائمة.';box.appendChild(d)}
  favBtn();
}
function markOn(){document.querySelectorAll('#list .ch').forEach(b=>b.classList.toggle('on',!!b._it&&b._it.url===curUrl))}
function favBtn(){const on=curUrl&&favs.has(curUrl);$('pf').textContent=on?'★ في المفضلة':'☆ أضف للمفضلة'}
function toggleFav(u){
  if(!u)return;
  if(favs.has(u))favs.delete(u);else favs.add(u);
  LS.set('m3u_favs',[...favs]);
  document.querySelectorAll('#list .st').forEach(s=>{const on=favs.has(s._u);s.classList.toggle('on',on);s.textContent=on?'★':'☆'});
  favBtn();if($('grp').value==='__fav')render();
  syncFavList();
}
$('pf').onclick=()=>toggleFav(curUrl);

function stopAll(){
  if(hls){try{hls.destroy()}catch(e){}hls=null}
  if(mp){try{mp.destroy()}catch(e){}mp=null}
  const v=$('v');v.onerror=null;v.removeAttribute('src');v.load();
}
function modesFor(u){
  if(/\.m3u8?(\?|$)/i.test(u))return['hls','native','mpegts'];
  if(/\.(ts|flv)(\?|$)/i.test(u))return['mpegts','hls','native'];
  return['native','hls','mpegts'];
}
function play(it){
  curUrl=it.url;
  recs=[it.url].concat(recs.filter(u=>u!==it.url)).slice(0,30);LS.set('m3u_recs',recs);
  LS.set('m3u_last',{url:it.url,name:it.name,listId:active,ts:Date.now()});
  $('now').textContent=it.name;
  setMsg('');favBtn();
  $('resumebar').classList.add('hide');
  
  // إظهار رقم القناة في شاشة OSD
  const channelIndex = shown.findIndex(i=>i.url===it.url) + 1;
  const osd = $('osd');
  if(osd && channelIndex > 0) {
    osd.textContent = `القناة ${channelIndex} : ${it.name}`;
    osd.style.display = 'block';
    setTimeout(() => { osd.style.display = 'none'; }, 3000); // إخفاء بعد 3 ثواني
  }

  const v=$('v'),my=++tok,modes=modesFor(it.url);let i=0;
  const next=()=>{
    if(my!==tok)return;
    if(i>=modes.length){setMsg('تعذر تشغيل هذا الرابط. قد يكون متوقفاً أو محجوباً أو بصيغة لا يدعمها المتصفح.',true);return}
    const m=modes[i++];stopAll();
    const fail=()=>{if(my===tok)next()};
    try{
      if(m==='hls'){
        if(!(window.Hls&&Hls.isSupported()))return next();
        hls=new Hls();hls.loadSource(it.url);hls.attachMedia(v);
        hls.on(Hls.Events.ERROR,(e,d)=>{if(d.fatal)fail()});
      }else if(m==='mpegts'){
        if(!(window.mpegts&&mpegts.isSupported()))return next();
        mp=mpegts.createPlayer({type:/\.flv(\?|$)/i.test(it.url)?'flv':'mpegts',isLive:true,url:it.url});
        mp.attachMediaElement(v);mp.load();
        mp.on(mpegts.Events.ERROR,fail);
      }else{
        v.onerror=fail;v.src=it.url;
      }
      v.play().catch(()=>{});
    }catch(e){fail()}
  };
  next();markOn();
}
function step(d){
  if(!shown.length)return;
  const k=shown.findIndex(i=>i.url===curUrl);
  play(shown[k<0?0:(k+d+shown.length)%shown.length]);
}
$('prev').onclick=()=>step(-1);$('nextb').onclick=()=>step(1);
function fs(){const v=$('v');if(document.fullscreenElement)document.exitFullscreen();else if(v.requestFullscreen)v.requestFullscreen().catch(()=>{})}
async function pip(){try{if(document.pictureInPictureElement)await document.exitPictureInPicture();else await $('v').requestPictureInPicture()}catch(e){setMsg('وضع صورة داخل صورة غير متاح الآن.',true)}}
$('fsb').onclick=fs;$('pipb').onclick=pip;

$('unmute').onclick=()=>{const v=$('v');v.muted=false;v.volume=1;$('resumebar').classList.add('hide')};
$('resumex').onclick=()=>$('resumebar').classList.add('hide');

async function tryResume(){
  if(!LS.get('m3u_resume',true))return false;
  const last=LS.get('m3u_last');
  if(!last||!last.listId||!last.url)return false;
  const list=lists.find(l=>l.id===last.listId);
  if(!list)return false;
  const parsed=parseM3U(list.text);
  const item=parsed.find(i=>i.url===last.url);
  if(!item)return false;
  if(active!==list.id)openList(list.id);
  play(item);
  const v=$('v');
  try{
    v.muted=false;
    await v.play();
  }catch(e){
    v.muted=true;
    try{await v.play()}catch(e2){}
    $('resumebar').classList.remove('hide');
  }
  setMsg('▶ استئناف: '+item.name);
  return true;
}

const resumeChk=$('resume');
resumeChk.checked=LS.get('m3u_resume',true);
resumeChk.onchange=e=>LS.set('m3u_resume',e.target.checked);

document.addEventListener('keydown',e=>{
  const t=e.target.tagName;
  if(/INPUT|TEXTAREA|SELECT/.test(t)||document.querySelector('dialog[open]')||e.ctrlKey||e.altKey||e.metaKey)return;
  const k=e.key.toLowerCase(),v=$('v');
  if(k==='f')fs();
  else if(k==='p')pip();
  else if(k==='m')v.muted=!v.muted;
  else if(k==='b')toggleFav(curUrl);
  else if(k==='t')cycleTheme();
  else if(k==='/'){e.preventDefault();$('q').focus()}
  else if(k==='arrowup'||k==='pageup'){e.preventDefault();step(-1)}
  else if(k==='arrowdown'||k==='pagedown'){e.preventDefault();step(1)}
  else if(k===' '&&t!=='BUTTON'&&t!=='VIDEO'){e.preventDefault();v.paused?v.play().catch(()=>{}):v.pause()}
  // دعم أرقام الريموت (0-9) للتنقل السريع
  else if (k >= '0' && k <= '9') {
    const idx = parseInt(k);
    if (shown[idx]) {
      play(shown[idx]);
    }
  }
});

function openSet(){$('cf').value='';$('forget').disabled=true;mgr();$('dlg').showModal()}
$('set').onclick=openSet;$('welcomeAdd').onclick=openSet;$('close').onclick=()=>$('dlg').close();
$('upd').onclick=()=>updateList(active);
$('lists').onchange=e=>openList(e.target.value);
function readText(f){return new Promise(ok=>{const r=new FileReader();r.onload=()=>ok(r.result);r.onerror=()=>ok('');r.readAsText(f,'UTF-8')})}
async function addFiles(fs_){
  let okAny=false;
  for(const f of fs_){if(await addList(await readText(f),f.name.replace(/\.[^.]+$/,'')))okAny=true}
  if(okAny)$('dlg').close();
}
$('file').onchange=e=>{const a=[...e.target.files];e.target.value='';a.length&&addFiles(a)};
$('mfile').onchange=async e=>{
  const f=e.target.files[0];e.target.value='';const l=lists.find(x=>x.id===mergeTarget);if(!f||!l)return;
  const n=parseM3U(await readText(f));
  if(!n.length){setMsg('الملف لا يحتوي قائمة M3U مقروءة.',true);return}
  const old=parseM3U(l.text),had=new Set(old.map(i=>i.url)),out=merge(old,n,false);
  l.text=ser(out);l.n=out.length;l.upd=Date.now();await saveLists();
  if(l.id===active)openList(l.id);mgr();
  setMsg('تم دمج الملف في «'+l.name+'»: '+n.filter(i=>!had.has(i.url)).length+' قناة جديدة، المجموع '+out.length+'.');
};
$('fetch').onclick=async()=>{
  const u=$('url').value.trim();if(!u){setMsg('أدخل رابط القائمة أولاً.',true);return}
  setMsg('جارٍ الجلب...');
  try{
    const t=await getText(u);let nm='';
    try{nm=new URL(u).hostname}catch(e){}
    if(await addList(t,nm,u)){$('url').value='';$('dlg').close()}
  }catch(e){setMsg('تعذر جلب الرابط (قد يمنعه المتصفح أو الموقع).',true)}
};

// زر إضافة Extreme
$('xt_add').onclick = () => {
  const host = $('xt_host').value.trim();
  const user = $('xt_user').value.trim();
  const pass = $('xt_pass').value.trim();
  if(host && user && pass) {
    loadXtreamCodes(host, user, pass, 'سيرفر Extreme');
  } else {
    setMsg('يرجى ملء جميع الحقول الخاصة بالسيرفر.', true);
  }
};

$('cf').oninput=e=>{$('forget').disabled=e.target.value.trim()!=='مسح'};
$('forget').onclick=async()=>{
  tok++;stopAll();await DB.del('lists');await DB.del('list');
  LS.del('m3u_favs');LS.del('m3u_recs');LS.del('m3u_fav_meta');LS.del('m3u_last');LS.del('m3u_skip_upd');
  favs=new Set();recs=[];lists=[];active='';curUrl='';
  fillLists();showWelcome();$('dlg').close();setMsg('تم مسح كل القوائم والمفضلة.');
};
let qT;$('q').oninput=()=>{clearTimeout(qT);qT=setTimeout(render,120)};
let gT;$('grp').onchange=()=>{clearTimeout(gT);gT=setTimeout(render,80)};
['dragover','dragenter'].forEach(ev=>document.addEventListener(ev,e=>{e.preventDefault();$('drop').classList.add('over')}));
['dragleave','drop'].forEach(ev=>document.addEventListener(ev,e=>{e.preventDefault();$('drop').classList.remove('over')}));
document.addEventListener('drop',e=>{const a=[...e.dataTransfer.files];a.length&&addFiles(a)});

/* ---------- فحص التحديثات من المدونة ---------- */
const APP_VERSION=(window.Android&&Android.version())||'1.6.0';
const UPDATE_PAGE='https://wasslatv.blogspot.com/p/version.html';

$('ver').textContent=APP_VERSION;$('ver2').textContent=APP_VERSION;$('fv').textContent=APP_VERSION;

function compareVer(a,b){
  const pa=String(a).split('.').map(Number),pb=String(b).split('.').map(Number);
  for(let i=0;i<Math.max(pa.length,pb.length);i++){
    const x=pa[i]||0,y=pb[i]||0;
    if(x>y)return 1;
    if(x<y)return -1;
  }
  return 0;
}

async function checkUpdates(silent){
  if(window.Android)return;
  try{
    const r=await fetch(UPDATE_PAGE+'?t='+Date.now(),{cache:'no-store'});
    if(!r.ok){if(!silent)setMsg('تعذر الوصول لصفحة التحديثات.',true);return false}
    const text=await r.text();
    const get=id=>{
      const m=text.match(new RegExp('id=["\']'+id+'["\'][^>]*>([\\s\\S]*?)<','i'));
      return m?m[1].trim():'';
    };
    const ver=get('ver'), dl=get('dl'), notes=get('notes');
    if(!ver||!dl){if(!silent)setMsg('صفحة التحديثات لا تحتوي بيانات صحيحة.',true);return false}
    if(compareVer(ver,APP_VERSION)<=0){
      if(!silent)setMsg('✓ لديك أحدث إصدار ('+APP_VERSION+')');
      return false;
    }
    if(LS.get('m3u_skip_upd')===ver){
      if(!silent)setMsg('يوجد إصدار '+ver+' (تجاهلته سابقاً).');
      return true;
    }
    $('updver').textContent=ver;
    $('updnotes').textContent=notes||'';
    $('updlink').href=dl;
    $('updbar').classList.remove('hide');
    if(!silent)setMsg('إصدار جديد '+ver+' متاح.');
    return true;
  }catch(e){
    if(!silent)setMsg('تعذر فحص التحديثات (قد يمنع المتصفح الاتصال).',true);
    return false;
  }
}

$('updskip').onclick=()=>{
  const v=$('updver').textContent;
  if(v)LS.set('m3u_skip_upd',v);
  $('updbar').classList.add('hide');
};
$('checkupd').onclick=()=>checkUpdates(false);

/* ---------- startup ---------- */
(async()=>{
  let d=null;try{d=await DB.get('lists')}catch(e){}
  if(d&&d.lists && d.lists.length > 0){
    lists=d.lists;active=d.active;
  } else {
    // إذا كان التطبيق جديداً، أضف القائمة المدمجة تلقائياً
    lists=[{id:'builtin',name:'القنوات',text:window.Android?'#EXTM3U\n':BUILT_IN_M3U,url:'',n:window.Android?0:parseM3U(BUILT_IN_M3U).length,upd:Date.now()}];
    active='builtin';saveLists();
  }
  if(favs.size&&!lists.find(l=>l.id===FAV_LIST_ID)){
    await syncFavList();
  }
  if(active===FAV_LIST_ID)active=lists.find(l=>l.id!==FAV_LIST_ID)?.id||'';
  fillLists();
  const normals=lists.filter(l=>l.id!==FAV_LIST_ID);
  if(normals.length)openList(normals.some(l=>l.id===active)?active:normals[0].id);
  else if(lists.find(l=>l.id===FAV_LIST_ID))openList(FAV_LIST_ID);
  else showWelcome();
  setTimeout(()=>tryResume(),250);
  setTimeout(()=>checkUpdates(true),1500);
})();
