/* ===== حالة انتظار البث والتوقف: لا نغيّر القناة تلقائياً ===== */
(function(){
  const box=$('ldg'),v=$('v'),nm=$('ldgn'),st=$('ldgs'),lg=$('ldgl');
  let cur=null,slow=null,gone=null,stallTimer=null,watchdog=null,lastTime=0,lastAdvance=0,hasPlayed=false;
  const clearWait=()=>{clearTimeout(slow);clearTimeout(gone);clearTimeout(stallTimer)};
  const hide=()=>{
    box.classList.remove('on','bad');
    clearWait();
  };
  function bad(msg){
    clearWait();
    st.textContent=msg;
    box.classList.add('on','bad');
  }
  function show(it){
    cur=it;hasPlayed=false;lastTime=0;lastAdvance=Date.now();
    nm.textContent=it.name;
    if(it.logo){lg.src=it.logo;lg.style.display=''}else lg.style.display='none';
    st.textContent='';
    box.classList.remove('bad');box.classList.add('on');
    clearWait();
    slow=setTimeout(()=>{if(box.classList.contains('on')&&!box.classList.contains('bad'))st.textContent='الاتصال بطيء';},12000);
    gone=setTimeout(()=>bad('تعذّر تشغيل القناة'),30000);
  }
  lg.onerror=()=>{lg.style.display='none'};
  v.addEventListener('playing',()=>{
    hasPlayed=true;lastTime=v.currentTime;lastAdvance=Date.now();hide();
  });
  function buffering(){
    if(!cur||v.paused||v.ended)return;
    clearTimeout(stallTimer);
    stallTimer=setTimeout(()=>{
      if(cur&&!v.paused&&!v.ended&&v.readyState<3)bad('توقف البث');
    },10000);
  }
  v.addEventListener('waiting',buffering);
  v.addEventListener('stalled',buffering);
  v.addEventListener('error',()=>{if(cur)bad('تعذّر تشغيل القناة')});
  v.addEventListener('ended',()=>{if(cur)bad('انتهى البث')});
  v.addEventListener('timeupdate',()=>{
    if(!cur||v.paused||v.ended)return;
    if(v.currentTime!==lastTime){lastTime=v.currentTime;lastAdvance=Date.now();}
  });
  watchdog=setInterval(()=>{
    if(!cur||!hasPlayed||v.paused||v.ended||box.classList.contains('bad'))return;
    if(Date.now()-lastAdvance>15000)bad('توقف البث');
  },3000);
  const _p=play;
  play=function(it){
    if(!(it&&it.url===curUrl&&!v.paused))show(it);
    return _p(it);
  };
  const _m=window.setMsg;
  window.setMsg=function(t,err){
    _m(t,err);
    if(err&&box.classList.contains('on'))bad('تعذّر تشغيل القناة');
  };
  // تبقى القناة الحالية محددة؛ المستخدم هو من يقرر إعادة المحاولة أو الانتقال.
  $('ldgr').onclick=()=>{if(cur)play(cur)};
  window.__ldgBad=()=>box.classList.contains('bad');
  window.__ldgRetry=()=>{if(cur)play(cur)};
  $('ldgx').onclick=()=>{if(typeof window.step==='function')step(1)};
  window.addEventListener('beforeunload',()=>clearInterval(watchdog),{once:true});
})();
