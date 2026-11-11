/* ===== مكتبة الأفلام والمسلسلات (VOD): وضع مستقل بمعزل عن البث المباشر =====
   • تتعرف على الأفلام والمسلسلات في كل مصادرك (detectKind) وتصنّفها حسب المجموعة.
   • المسلسلات تُجمَّع بالمواسم والحلقات من أسماء مثل S01E02 أو «الموسم 1 الحلقة 2».
   • أي مصدر جديد فيه أفلام يظهر هنا تلقائياً (إضافة مكتبات لاحقاً = إضافة مصدر).
   التنقل: ↑ ↓ بين الصفوف · ← → داخل الصف · OK فتح · رجوع خطوة للخلف. */
(function(){
  const el=$('vod'),bodyEl=$('vodb'),tabsEl=$('vtabs'),B=document.body;
  let srcId='',on=false,idx=null,idxSig='',filter='all',rowsData=[],rIdx=0,cols=[],zone='rows',tIdx=0;
  let screen='home',cur=null,curEps=[],sIdx=0,eIdx=0,playing=null,lastLive=null;
  const FILTERS=['all','movies','series'];
  const rtl=()=>getComputedStyle(document.documentElement).direction==='rtl';
  const toast=t=>{if(window.__rcvToast)window.__rcvToast(t)};
  window.__vodOn=()=>on;

  /* ---------- التحليل والتصنيف ---------- */
  const EP1=/^(.*?)[\s._-]*[sS](\d{1,2})[\s._-]*[eE](\d{1,3})/;
  const EP2=/^(.*?)\s*(?:الموسم|موسم|season)\s*(\d+)\s*(?:الحلقة|حلقة|episode|ep)\s*(\d+)/i;
  const EP3=/^(.*?)\s*(?:الحلقة|حلقة|episode|ep)\s*[:.-]?\s*(\d+)/i;
  function epInfo(name){
    let m=EP1.exec(name);if(m)return{title:m[1].trim(),s:+m[2],e:+m[3]};
    m=EP2.exec(name);if(m)return{title:m[1].trim(),s:+m[2],e:+m[3]};
    m=EP3.exec(name);if(m)return{title:m[1].trim(),s:1,e:+m[2]};
    return null;
  }
  const sig=()=>lists.filter(l=>l.id!==FAV_LIST_ID).map(l=>l.id+':'+(l.upd||0)+':'+(l.n||0)).join('|');
  const tick=()=>new Promise(r=>setTimeout(r,0));

  async function build(){
    const s=sig()+'|'+srcId;if(idx&&s===idxSig)return;
    const movies=[],series=new Map();
    for(const l of lists){
      if(l.id===FAV_LIST_ID)continue;
      if(srcId&&l.id!==srcId)continue;
      const its=parseM3U(l.text);
      for(let i=0;i<its.length;i++){
        const it=its[i],k=detectKind(it);
        if(k!=='movie'&&k!=='series')continue;
        if(k==='series'&&/action=get_series_info/.test(it.url)){
          series.set('xt:'+it.url,{kind:'series',title:it.name,logo:it.logo||'',group:it.group||'مسلسلات',eps:[],lazy:it.url});
        }else if(k==='series'){
          const ei=epInfo(it.name)||{title:it.name,s:1,e:1};
          const t=(ei.title||it.name).replace(/[._]+/g,' ').trim(),key=t.toLowerCase();
          let sr=series.get(key);
          if(!sr){sr={kind:'series',title:t,logo:it.logo||'',group:it.group||'مسلسلات',eps:[]};series.set(key,sr)}
          if(!sr.logo&&it.logo)sr.logo=it.logo;
          sr.eps.push({s:ei.s,e:ei.e,name:it.name,url:it.url,item:it});
        }else movies.push({kind:'movie',title:it.name,logo:it.logo||'',group:it.group||'أفلام',item:it});
        if(i%1500===1499)await tick();
      }
      await tick();
    }
    series.forEach(sr=>sr.eps.sort((a,b)=>a.s-b.s||a.e-b.e));
    idx={movies,series:[...series.values()]};idxSig=s;
  }
  function makeRows(){
    const all=[];
    if(filter!=='series')all.push(...idx.movies);
    if(filter!=='movies')all.push(...idx.series);
    const rows=[];
    if(!all.length)return rows;
    rows.push({title:'أُضيفت حديثاً',cards:all.slice(-40).reverse()});
    const by=new Map();
    all.forEach(c=>{const g=c.group||'أخرى';if(!by.has(g))by.set(g,[]);by.get(g).push(c)});
    [...by.entries()].sort((a,b)=>b[1].length-a[1].length).slice(0,40)
      .forEach(([g,cs])=>rows.push({title:g+' ('+cs.length+')',cards:cs.slice(0,60)}));
    return rows;
  }

  /* ---------- الرسم ---------- */
  function updateHero(c){
    if(!c)return;
    const img=$('vpimg');
    if(c.logo){img.src=c.logo;img.style.display=''}else img.style.display='none';
    $('vpt').textContent=c.title;
    $('vps').textContent=(c.kind==='series'?'مسلسل'+(c.eps.length?' · '+c.eps.length+' حلقة':''):'فيلم')+(c.group?' · '+c.group:'');
  }
  function paintTabs(){
    tabsEl.textContent='';
    [['all','الكل'],['movies','🎬 أفلام'],['series','🎞 مسلسلات']].forEach(([k,l],i)=>{
      const b=document.createElement('span');
      b.className='vt'+(filter===k?' on':'')+(zone==='tabs'&&i===tIdx?' sel':'');
      b.textContent=l;
      b.onclick=()=>{filter=k;zone='rows';idxRows();paintHome()};
      tabsEl.appendChild(b);
    });
  }
  function msgBody(t){
    bodyEl.textContent='';
    const d=document.createElement('div');d.className='vempty';d.textContent=t;bodyEl.appendChild(d);
  }
  function idxRows(){rowsData=makeRows();rIdx=0;cols=[]}
  function paintHome(){
    screen='home';paintTabs();bodyEl.textContent='';
    if(!rowsData.length){
      msgBody('لا توجد أفلام أو مسلسلات في مصادرك بعد.\nأضف مصدراً فيه مكتبة (ملف أو رابط أو Xtream) من الإعدادات ← إضافة مصدر، وستظهر هنا تلقائياً.');
      return;
    }
    rowsData.forEach((r,ri)=>{
      const row=document.createElement('div');row.className='vrow';
      const t=document.createElement('div');t.className='vrt';t.textContent=r.title;row.appendChild(t);
      const cs=document.createElement('div');cs.className='vcs';
      r.cards.forEach((c,ci)=>{
        const d=document.createElement('div');d.className='vc';d.dataset.i=((c.title||'?').trim()[0])||'?';
        if(c.logo){
          const img=document.createElement('img');img.loading='lazy';img.alt='';img.src=c.logo;
          img.onerror=()=>{img.remove();d.classList.add('noimg')};
          d.appendChild(img);
        }else d.classList.add('noimg');
        const nm=document.createElement('span');nm.className='vn';nm.textContent=c.title;d.appendChild(nm);
        if(c.kind==='series'){const b=document.createElement('i');b.textContent='مسلسل';d.appendChild(b)}
        d.onclick=()=>{rIdx=ri;cols[ri]=ci;zone='rows';markCards();activate(c)};
        cs.appendChild(d);
      });
      row.appendChild(cs);bodyEl.appendChild(row);
    });
    markCards();
  }
  function markCards(){
    bodyEl.querySelectorAll('.vc.sel').forEach(x=>x.classList.remove('sel'));
    paintTabs();
    if(zone!=='rows'||!rowsData.length)return;
    const row=bodyEl.children[rIdx];if(!row)return;
    const c=row.querySelectorAll('.vc')[cols[rIdx]||0];
    row.scrollIntoView({block:'nearest'});
    if(c){c.classList.add('sel');c.scrollIntoView({block:'nearest',inline:'nearest'});updateHero(rowsData[rIdx].cards[cols[rIdx]||0])}
  }
  /* حلقات مسلسلات Xtream: تُحمَّل عند الفتح فقط */
  async function loadLazy(c){
    toast('جارٍ تحميل الحلقات…');
    try{
      const r=await fetch(c.lazy);if(!r.ok)return false;
      const j=await r.json(),u=new URL(c.lazy),base=u.origin,usr=u.searchParams.get('username'),pw=u.searchParams.get('password');
      const eps=[],E=j&&j.episodes;
      const add=(e,season)=>{
        if(!e||!e.id)return;
        const url=base+'/series/'+usr+'/'+pw+'/'+e.id+'.'+(e.container_extension||'mp4');
        const title=e.title||('الحلقة '+(e.episode_num||''));
        eps.push({s:+(e.season||season)||1,e:+e.episode_num||eps.length+1,name:title,url,item:{name:title,url,logo:c.logo,group:c.group,raw:'',extra:[]}});
      };
      if(Array.isArray(E))E.forEach(a=>Array.isArray(a)?a.forEach(e=>add(e,0)):add(a,0));
      else if(E&&typeof E==='object')Object.keys(E).forEach(sk=>{if(Array.isArray(E[sk]))E[sk].forEach(e=>add(e,+sk))});
      if(!eps.length)return false;
      eps.sort((a,b)=>a.s-b.s||a.e-b.e);c.eps=eps;return true;
    }catch(e){return false}
  }
  function activate(c){
    if(c.kind==='series'&&c.lazy&&!c.eps.length){
      loadLazy(c).then(ok=>{if(ok&&on){cur=c;sIdx=0;eIdx=0;zone='eps';paintSeries()}else if(!ok)toast('تعذّر تحميل حلقات هذا المسلسل')});
      return;
    }
    if(c.kind==='series'){cur=c;sIdx=0;eIdx=0;zone='eps';paintSeries()}
    else playItem(c.item,null,0);
  }

  /* ---------- شاشة المسلسل ---------- */
  function paintSeries(){
    screen='series';bodyEl.textContent='';
    const seasons=[...new Set(cur.eps.map(e=>e.s))].sort((a,b)=>a-b);
    if(sIdx>=seasons.length)sIdx=0;
    const bar=document.createElement('div');bar.className='vseas';
    seasons.forEach((s,i)=>{
      const b=document.createElement('span');b.className='vs'+(i===sIdx?' on':'')+(zone==='seasons'&&i===sIdx?' sel':'');
      b.textContent='الموسم '+s;b.onclick=()=>{sIdx=i;eIdx=0;zone='eps';paintSeries()};
      bar.appendChild(b);
    });
    const list=document.createElement('div');list.className='veps';
    curEps=cur.eps.filter(e=>e.s===seasons[sIdx]);
    curEps.forEach((e,i)=>{
      const r=document.createElement('div');r.className='ve'+(zone==='eps'&&i===eIdx?' sel':'');
      r.innerHTML='<span class="n"></span><span class="t"></span>';
      r.children[0].textContent='الحلقة '+e.e;r.children[1].textContent=e.name;
      r.onclick=()=>{eIdx=i;zone='eps';playItem(e.item,curEps,i)};
      list.appendChild(r);
    });
    bodyEl.appendChild(bar);bodyEl.appendChild(list);
    const s=list.querySelector('.sel');if(s)s.scrollIntoView({block:'nearest'});
    updateHero(cur);
  }
  function seriesKey(k){
    const r=rtl(),fwd=r?'ArrowLeft':'ArrowRight',bwd=r?'ArrowRight':'ArrowLeft';
    const nSeasons=bodyEl.querySelectorAll('.vs').length;
    if(zone==='seasons'){
      if(k===fwd&&sIdx<nSeasons-1){sIdx++;eIdx=0;paintSeries()}
      else if(k===bwd&&sIdx>0){sIdx--;eIdx=0;paintSeries()}
      else if(k==='ArrowDown'||k==='Enter'){zone='eps';paintSeries()}
    }else{
      if(k==='ArrowUp'){if(eIdx>0)eIdx--;else zone='seasons';paintSeries()}
      else if(k==='ArrowDown'){eIdx=Math.min(curEps.length-1,eIdx+1);paintSeries()}
      else if(k==='Enter'){const e=curEps[eIdx];if(e)playItem(e.item,curEps,eIdx)}
    }
  }

  /* ---------- الرئيسية ---------- */
  function homeKey(k){
    const r=rtl(),fwd=r?'ArrowLeft':'ArrowRight',bwd=r?'ArrowRight':'ArrowLeft';
    if(zone==='tabs'){
      if(k===fwd)tIdx=Math.min(FILTERS.length-1,tIdx+1);
      else if(k===bwd)tIdx=Math.max(0,tIdx-1);
      else if(k==='ArrowDown'){zone='rows'}
      else if(k==='Enter'){filter=FILTERS[tIdx];zone='rows';idxRows();paintHome();return}
      markCards();return;
    }
    if(!rowsData.length){if(k==='ArrowUp'){zone='tabs';tIdx=FILTERS.indexOf(filter);markCards()}return}
    const n=rowsData[rIdx].cards.length;
    if(k==='ArrowUp'){if(rIdx>0)rIdx--;else{zone='tabs';tIdx=FILTERS.indexOf(filter)}}
    else if(k==='ArrowDown'){rIdx=Math.min(rowsData.length-1,rIdx+1)}
    else if(k===fwd){cols[rIdx]=Math.min(n-1,(cols[rIdx]||0)+1)}
    else if(k===bwd){cols[rIdx]=Math.max(0,(cols[rIdx]||0)-1)}
    else if(k==='Enter'){activate(rowsData[rIdx].cards[cols[rIdx]||0]);return}
    markCards();
  }

  /* ---------- التشغيل ---------- */
  const fmt=s=>{s=Math.max(0,Math.floor(s));const h=Math.floor(s/3600),m=Math.floor(s%3600/60),x=s%60;return(h?h+':'+String(m).padStart(2,'0'):m)+':'+String(x).padStart(2,'0')};
  function playItem(it,eps,i){
    playing=eps?{eps,i}:null;
    B.classList.add('cine-play');
    play(it);
  }
  function stopPlay(){tok++;stopAll();B.classList.remove('cine-play');playing=null}
  function seek(sec){
    const v=$('v');
    if(!isFinite(v.duration)||!v.duration){toast('لا يمكن التقديم في هذا المحتوى');return}
    v.currentTime=Math.max(0,Math.min(v.duration,v.currentTime+sec));
    toast(fmt(v.currentTime)+' / '+fmt(v.duration));
  }
  function playKey(k){
    const v=$('v');
    if(k==='Enter'){if(v.paused)v.play();else v.pause();toast(v.paused?'⏸ متوقف':'▶ تشغيل')}
    else if(k==='ArrowRight'||k==='ArrowLeft')seek(k==='ArrowRight'?10:-10);
    else if(k==='ArrowUp'||k==='ArrowDown')seek(k==='ArrowUp'?60:-60);
    else if((k==='PageDown'||k==='PageUp')&&playing){
      const n=playing.i+(k==='PageDown'?1:-1);
      if(n>=0&&n<playing.eps.length){playing.i=n;eIdx=n;play(playing.eps[n].item)}
    }
  }
  $('v').addEventListener('ended',()=>{
    if(!on||!B.classList.contains('cine-play')||!playing)return;
    const n=playing.i+1;
    if(n<playing.eps.length){playing.i=n;eIdx=n;play(playing.eps[n].item)}
  });

  /* ---------- فتح وإغلاق ---------- */
  function open(){
    if(on)return;
    on=true;
    lastLive=shown.find(i=>i.url===curUrl)||null;
    tok++;stopAll();
    el.classList.add('on');B.classList.add('vod');
    zone='rows';screen='home';tabsEl.textContent='';
    msgBody('جارٍ تحليل المكتبة…');
    build().then(()=>{idxRows();paintHome()}).catch(()=>msgBody('تعذّر تحليل المكتبة'));
  }
  function close(){
    on=false;el.classList.remove('on');B.classList.remove('vod');B.classList.remove('cine-play');
    if(lastLive){const it=lastLive;lastLive=null;play(it)}
  }
  window.__openVod=function(id){srcId=(typeof id==='string')?id:'';open()};
  window.__vodBack=function(){
    if(!on)return false;
    if(B.classList.contains('cine-play')){stopPlay();return true}
    if(screen==='series'){zone='rows';paintHome();return true}
    close();return true;
  };
  document.addEventListener('keydown',e=>{
    if(!on||document.querySelector('dialog[open]'))return;
    if(e.ctrlKey||e.altKey||e.metaKey)return;
    e.preventDefault();e.stopPropagation();
    const k=e.key;
    if(B.classList.contains('cine-play'))playKey(k);
    else if(screen==='series')seriesKey(k);
    else homeKey(k);
  },true);
})();
