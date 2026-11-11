/* ===== الريسيفر: البث المباشر بملء الشاشة + قائمة القنوات (قائمة وشاشة فقط) =====
   البث:   ↑ القناة الأعلى رقماً، ↓ القناة الأدنى رقماً، ← → تقديم/تأخير، OK قائمة القنوات
   القائمة: ↑ ↓ القنوات، ← → الفئات، رجوع إلى البث */
(function(){
  const rb=$('rbanner'),rn=$('rnum'),rt=$('rtoast'),rm=$('rmenu'),rows=rm.querySelector('.rows'),pv=rm.querySelector('.pv'),
        rx=$('rexit'),v=$('v'),im=rb.querySelector('img'),mi=rm.querySelector('.mi'),
        headEl=$('rhead'),hintEl=$('rhint'),np=$('npad'),npv=$('npv');
  let noSeekUrl='',on=false,buf='',bufT,banT,clkT,toastT,xT,open=false,sel=0,curIt=null,zone='rows',hIdx=0;
  const rtl=()=>getComputedStyle(document.documentElement).direction==='rtl';
  const numOf=(it,i)=>{const m=it&&it.raw&&/channel-number="(\d+)"/.exec(it.raw);return m?+m[1]:i+1};
  const pad=n=>String(n).padStart(2,'0');
  const hm=()=>{const d=new Date();return pad(d.getHours())+':'+pad(d.getMinutes())};
  const stOn=()=>window.__stOn&&window.__stOn();
  im.onerror=()=>{im.style.display='none'};

  /* ===== المصادر ← التصنيفات ← القنوات (ثلاثة مستويات، رجوع يتراجع خطوة) ===== */
  let level='rows',nav=[],panel=[];
  const kcache=new Map();
  function kindsOf(l){
    const key=l.id+':'+(l.upd||0)+':'+(l.n||0);let c=kcache.get(key);if(c)return c;
    c={live:0,vod:0,groups:new Map()};
    try{parseM3U(l.text).forEach(it=>{
      const k=detectKind(it);
      if(k==='movie'||k==='series')c.vod++;
      else{c.live++;const g=it.group||'بدون مجموعة';c.groups.set(g,(c.groups.get(g)||0)+1)}
    })}catch(e){}
    kcache.set(key,c);return c;
  }
  function srcData(){
    const out=[],all=(lists||[]).slice().sort((a,b)=>(a.id===FAV_LIST_ID?-1:0)-(b.id===FAV_LIST_ID?-1:0));
    all.forEach(l=>{
      if(l.id===FAV_LIST_ID&&!(l.n>0))return;
      const k=kindsOf(l),fav=l.id===FAV_LIST_ID;
      if(k.live>0||!k.vod)out.push({t:'src',id:l.id,ic:fav?'★':'📡',nm:fav?'المفضلة':l.name,ct:(k.live||l.n||0)+' قناة',k});
      if(k.vod>0&&!fav)out.push({t:'vod',id:l.id,ic:'🎬',nm:l.name+' — مكتبة',ct:k.vod+' عنصر'});
    });
    out.push({t:'set',ic:'⚙',nm:'الإعدادات',ct:''});
    return out;
  }
  function catData(){
    const l=(lists||[]).find(x=>x.id===active),k=l?kindsOf(l):{live:items.length,vod:0,groups:new Map()},out=[];
    const IC={'أخبار':'📰','MBC':'📺','دينية':'🕌','موسيقى':'🎵','إذاعات':'📻','الجزائر':'📍','رياضة':'⚽','أطفال':'🧸','أفلام':'🎬','وثائقي':'🌍'};
    out.push({v:k.vod>0?'__k_live':'',ic:'📺',nm:'كل القنوات',ct:(k.live||items.length)+' قناة'});
    const nf=items.filter(i=>favs.has(i.url)).length;if(nf)out.push({v:'__fav',ic:'★',nm:'المفضلة',ct:nf+' قناة'});
    const nr=recs.filter(u=>items.some(i=>i.url===u)).length;if(nr)out.push({v:'__rec',ic:'🕘',nm:'آخر ما شاهدت',ct:nr+' قناة'});
    k.groups.forEach((c,g)=>out.push({v:g,ic:IC[g]||'📁',nm:g,ct:c+' قناة'}));
    return out;
  }
  function buildPanel(){
    rows.textContent='';
    const fr=document.createDocumentFragment();
    panel.forEach((p,i)=>{
      const r=document.createElement('div');r.className='r pn'+((p.t==='src'&&p.id===active)||(p.v!==undefined&&p.v===$('grp').value)?' cur':'');
      r.innerHTML='<span class="n"></span><span class="t"><span></span></span><span class="s"></span>';
      r.children[0].textContent=p.ic||'›';r.children[1].firstChild.textContent=p.nm;r.children[2].textContent=p.ct||'';
      r.onclick=e=>{e.stopPropagation();sel=i;mark();pickPanel()};
      fr.appendChild(r);
    });
    rows.appendChild(fr);
    rm.querySelector('.mg').textContent=level==='sources'?'‹ المصادر › — اختر مصدراً':'‹ التصنيفات › — '+panel.length+' تصنيف';
  }
  function showSources(){panel=srcData();level='sources';buildPanel()}
  function showCats(){panel=catData();level='cats';buildPanel()}
  function openSources(){
    if(level==='sources'){setZone('rows');return}
    nav.push(level);showSources();
    const i=panel.findIndex(p=>p.t==='src'&&p.id===active);sel=Math.max(0,i);setZone('rows');
  }
  function pickPanel(){
    const p=panel[sel];if(!p)return;
    if(level==='sources'){
      if(p.t==='set'){openSettings();return}
      if(p.t==='vod'){closeL();if(window.__openVod)__openVod(p.id);return}
      openList(p.id);
      nav.push('sources');
      if(p.k&&p.k.groups.size>1){showCats();sel=0;setZone('rows')}
      else{$('grp').value=(p.k&&p.k.vod>0)?'__k_live':'';render();level='rows';build();sel=0;setZone('rows')}
    }else if(level==='cats'){
      $('grp').value=p.v;render();nav.push('cats');level='rows';build();sel=0;setZone('rows');
    }
  }
  function goBack(){
    const lv=nav.pop();
    if(!lv){closeL();return}
    level=lv;
    if(lv==='sources')showSources();else if(lv==='cats')showCats();else build();
    sel=0;
    if(lv==='rows')sel=Math.max(0,shown.findIndex(i=>i.url===curUrl));
    setZone('rows');
  }

  function badges(it){
    const h=v.videoHeight,w=v.videoWidth,u=((it&&it.url)||'').toLowerCase(),a=[];
    if(h)a.push((h>=1080?'FHD':h>=720?'HD':'SD')+' '+w+'×'+h);
    a.push(/\.m3u8?(\?|$)/.test(u)?'HLS':/\.ts(\?|$)/.test(u)?'TS':'IPTV');
    if(it&&favs.has(it.url))a.push('★');
    rb.querySelector('.bg').innerHTML=a.map(x=>'<i>'+x+'</i>').join('');
  }
  function clock(){rb.querySelector('.rc').textContent=hm();rm.querySelector('.mc').textContent=hm()}
  function banner(it){
    curIt=it;const i=shown.findIndex(x=>x.url===it.url);
    rb.querySelector('.rn').textContent=i>=0?numOf(it,i):'–';
    rb.querySelector('b').textContent=it.name;rb.querySelector('small').textContent=it.group||'';
    rb.querySelector('.dt').textContent=new Date().toLocaleDateString('ar-DZ',{weekday:'long',day:'numeric',month:'long'});
    if(it.logo){im.src=it.logo;im.style.display=''}else im.style.display='none';
    badges(it);clock();rb.classList.add('on');clearInterval(clkT);clkT=setInterval(clock,20000);
    clearTimeout(banT);banT=setTimeout(()=>{rb.classList.remove('on');clearInterval(clkT)},4500);
  }
  ['resize','loadedmetadata'].forEach(ev=>v.addEventListener(ev,()=>{if(rb.classList.contains('on')&&curIt)badges(curIt)}));
  function toast(t){rt.textContent=t;rt.classList.add('on');clearTimeout(toastT);toastT=setTimeout(()=>rt.classList.remove('on'),1800)}

  function digit(d){if(buf.length>=4)buf='';buf+=d;rn.textContent=buf;rn.style.display='block';clearTimeout(bufT);bufT=setTimeout(commit,1500)}
  function commit(){
    clearTimeout(bufT);const n=parseInt(buf,10);buf='';rn.style.display='none';if(!n)return;
    const hit=shown.find((x,i)=>numOf(x,i)===n);
    if(!hit){toast('القناة '+n+' غير موجودة');return}play(hit);
  }
  np.querySelectorAll('[data-d]').forEach(b=>b.onclick=()=>{if(npv.textContent.length<4)npv.textContent+=b.dataset.d});
  $('npdel').onclick=()=>{npv.textContent=npv.textContent.slice(0,-1)};
  $('npgo').onclick=()=>{buf=npv.textContent;np.close();commit()};
  $('npcan').onclick=()=>np.close();
  function openNumpad(){npv.textContent='';np.showModal()}

  function place(){if(!open)return;const r=pv.getBoundingClientRect(),s=v.style;s.position='fixed';s.left=r.left+'px';s.top=r.top+'px';s.width=r.width+'px';s.height=r.height+'px'}
  window.addEventListener('resize',place);
  function build(){
    if(level==='sources'){panel=srcData();return buildPanel()}
    if(level==='cats'){panel=catData();return buildPanel()}
    rows.textContent='';const fr=document.createDocumentFragment();
    shown.slice(0,1500).forEach((it,i)=>{
      const r=document.createElement('div');r.className='r'+(it.url===curUrl?' cur':'');
      r.innerHTML='<span class="n">'+numOf(it,i)+'</span><span class="t"></span><span class="s"></span>';
      {const sp=document.createElement('span');sp.textContent=it.name;r.children[1].appendChild(sp)}r.children[2].textContent=favs.has(it.url)?'★':'';
      r.onclick=e=>{e.stopPropagation();closeL();play(it)};fr.appendChild(r);
    });
    rows.appendChild(fr);const o=$('grp').selectedOptions[0];
    rm.querySelector('.mg').textContent='‹ '+(o?o.textContent:'')+' › — '+shown.length+' قناة';
  }
  function mark(){
    [...rows.children].forEach((r,i)=>r.classList.toggle('sel',i===sel));
    const r=rows.children[sel];if(r)r.scrollIntoView({block:'nearest'});
    rows.querySelectorAll('.mq').forEach(x=>{x.classList.remove('mq');x.parentNode.classList.remove('mqc')});
    if(r){const tt=r.querySelector('.t'),sp=tt&&tt.firstChild;if(sp){const d=sp.offsetWidth-tt.clientWidth;if(d>2){sp.style.setProperty('--mq',(rtl()?d+8:-d-8)+'px');sp.classList.add('mq');tt.classList.add('mqc')}}}
    const g=mi.querySelector('img');
    if(level!=='rows'){
      const p=panel[sel];mi.querySelector('b').textContent=p?p.nm:'';mi.querySelector('small').textContent=p?(p.ct||''):'';g.style.display='none';return;
    }
    const it=shown[sel];
    if(it){mi.querySelector('b').textContent=numOf(it,sel)+' · '+it.name;mi.querySelector('small').textContent=it.group||'';if(it.logo){g.src=it.logo;g.style.display=''}else g.style.display='none'}
    else{mi.querySelector('b').textContent='لا توجد قنوات في هذه الفئة';mi.querySelector('small').textContent='';g.style.display='none'}
  }
  function renderHead(){[...headEl.children].forEach((h,i)=>h.classList.toggle('sel',zone==='head'&&i===hIdx))}
  function renderHint(){
    const c=window.__caps||{};let h;
    if(zone==='head')h=['OK فتح المصادر','↓ إلى القائمة','رجوع'];
    else if(level==='sources')h=['↑ ↓ المصادر','OK اختيار','رجوع'];
    else if(level==='cats')h=['↑ ↓ التصنيفات','OK دخول','رجوع'];
    else h=['↑ ↓ القنوات','OK مشاهدة','← → تغيير التصنيف','↑ من أول القائمة: المصادر','رجوع'];
    hintEl.innerHTML=h.map(x=>'<span>'+x+'</span>').join('')+(c.color&&level==='rows'&&zone==='rows'?'<span><u class="c1"></u>مفضلة</span>':'');
  }
  document.addEventListener('caps',renderHint);
  function setZone(z){zone=z;rm.classList.toggle('z-rows',z==='rows');rm.classList.toggle('z-head',z==='head');renderHead();mark();renderHint()}
  function openL(){open=true;level='rows';nav=[];hIdx=0;build();sel=Math.max(0,shown.findIndex(i=>i.url===curUrl));document.body.classList.add('menu');rm.classList.add('on');v.classList.add('pvid');rb.classList.remove('on');clock();place();setZone('rows')}
  function closeL(){open=false;rm.classList.remove('on');document.body.classList.remove('menu');v.classList.remove('pvid');v.style.cssText=''}
  function favSel(){const it=shown[sel];if(!it)return;const s=sel;toggleFav(it.url);build();sel=Math.min(s,Math.max(0,rows.children.length-1));mark()}
  function cycleGroup(d){
    const o=[...$('grp').options];if(o.length<2)return;
    const i=(o.findIndex(x=>x.value===$('grp').value)+d+o.length)%o.length;
    $('grp').value=o[i].value;render();build();sel=0;setZone('rows');
  }
  function openSettings(){closeL();if(window.__openSettings)window.__openSettings()}
  function seekBy(sec){const sk=v.seekable;if(!sk||!sk.length){if(noSeekUrl!==curUrl){noSeekUrl=curUrl;toast('هذه القناة لا تدعم التقديم والتأخير')}return}const a=sk.start(0),b=sk.end(sk.length-1),t=Math.min(b,Math.max(a,v.currentTime+sec)),behind=Math.round(b-t);v.currentTime=t;toast(behind>3?'⏪ متأخر '+behind+' ث عن المباشر':'● مباشر')}

  function menuKey(k,repeat){
    const r=rtl(),next=r?'ArrowLeft':'ArrowRight',prev=r?'ArrowRight':'ArrowLeft';
    if(zone==='rows'){
      const n=rows.children.length;
      if(k==='ArrowUp'){if(repeat)setZone('head');else if(sel>0){sel--;mark()}else setZone('head')}
      else if(k==='ArrowDown'){sel=Math.min(Math.max(0,n-1),sel+1);mark()}
      else if(k==='PageUp'||k==='Home')setZone('head');
      else if(k==='PageDown') {sel=Math.min(Math.max(0,n-1),sel+8);mark()}
      else if(k===next){if(level==='rows')cycleGroup(1)}
      else if(k===prev){if(level==='rows')cycleGroup(-1)}
      else if(k==='Enter'){if(level!=='rows')pickPanel();else{const it=shown[sel];if(it){closeL();play(it)}}}
      else if(k==='Escape'||k==='Backspace')goBack();
      else if(k==='F1'&&level==='rows')favSel();
    }else{
      if(k===next){hIdx=Math.min(headEl.children.length-1,hIdx+1);renderHead()}
      else if(k===prev){hIdx=Math.max(0,hIdx-1);renderHead()}
      else if(k==='ArrowDown')setZone('rows');
      else if(k==='Enter'){
        const h=headEl.children[hIdx].dataset.h;
        if(h==='sources')openSources();
      }else if(k==='Escape'||k==='Backspace')setZone('rows');
    }
  }
  headEl.querySelectorAll('.h').forEach((h,i)=>h.onclick=e=>{e.stopPropagation();hIdx=i;openSources()});

  /* ↑ = القناة الأعلى رقماً، ↓ = الأدنى رقماً (مثل الريسيفر الحقيقي) مع التفاف عند الطرفين */
  function zapByNumber(dir){
    if(!shown.length)return;
    const ci=Math.max(0,shown.findIndex(it=>it.url===curUrl)),cn=numOf(shown[ci],ci);
    const c=shown.map((it,i)=>({it,n:numOf(it,i)}));
    const up=c.filter(x=>x.n>cn).sort((a,b)=>a.n-b.n),down=c.filter(x=>x.n<cn).sort((a,b)=>b.n-a.n);
    const t=dir>0?(up[0]||c.slice().sort((a,b)=>a.n-b.n)[0]):(down[0]||c.slice().sort((a,b)=>b.n-a.n)[0]);
    if(t)play(t.it);
  }
  function setMode(m,silent){on=m;document.body.classList.toggle('rcv',m);LS.set('m3u_rcv',m);v.controls=!m;closeL();rb.classList.remove('on');if(m){$('q').value='';$('grp').value='';render();if(!silent&&document.documentElement.requestFullscreen)document.documentElement.requestFullscreen().catch(()=>{})}else if(document.fullscreenElement===document.documentElement)document.exitFullscreen().catch(()=>{})}
  rx.onclick=e=>{e.stopPropagation();openSettings()};
  $('player').addEventListener('click',()=>{if(on)open?closeL():openL()});
  document.addEventListener('mousemove',()=>{if(!on)return;rx.classList.add('on');clearTimeout(xT);xT=setTimeout(()=>rx.classList.remove('on'),3000)});
  document.addEventListener('keydown',e=>{
    const t=e.target.tagName;
    if(/INPUT|TEXTAREA|SELECT/.test(t)||document.querySelector('dialog[open]')||stOn()||(window.__vodOn&&window.__vodOn())||(window.__qpOn&&window.__qpOn())||e.ctrlKey||e.altKey||e.metaKey)return;
    const k=e.key,stop=()=>{e.preventDefault();e.stopPropagation()};
    if(/^[0-9]$/.test(k)){stop();digit(k);return}
    if(!on)return;
    if(open){menuKey(k,e.repeat);stop();return}
    if(k==='Enter'){stop();if(!buf&&window.__ldgBad&&window.__ldgBad())window.__ldgRetry();else if(buf)commit();else openL()}
    else if(k==='Escape'){if(buf){stop();clearTimeout(bufT);buf='';rn.style.display='none'}}
    else if(k==='ArrowLeft'||k==='ArrowRight'){stop();seekBy(k==='ArrowRight'?10:-10)}
    else if(k==='ArrowUp'||k==='ArrowDown'){stop();zapByNumber(k==='ArrowUp'?1:-1)}
    else if(e.code==='KeyI'){stop();const it=shown.find(i=>i.url===curUrl);if(it)banner(it)}
  },true);
  const _play=play;play=function(it){_play(it);if(on)banner(it)};
  window.__rcv={set:setMode};window.__rcvState=()=>({on,open,zone});window.__rcvOpen=openL;window.__rcvOpenSources=function(){openL();openSources()};window.__rcvNumpad=function(){closeL();openNumpad()};window.__rcvClose=closeL;window.__rcvToast=toast;window.__zapNum=zapByNumber;
  window.__rcvRefresh=()=>{if(open){build();sel=Math.min(sel,Math.max(0,rows.children.length-1));mark()}};
})();
