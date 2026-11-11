/* ===== القائمة السريعة: أول رجوع من البث ===== */
(function(){
  const el=$('qp'),row=$('qpr'),hint=$('lhint');
  let on=false,idx=0,hT=null;
  const close=()=>{on=false;el.classList.remove('on');hintLive()};
  function syncMeta(){
    const srcName = (lists.find(l=>l.id===active) || {name:'المصدر'}).name;
    const srcEl = document.getElementById('qpsrc');
    const chEl = document.getElementById('qpchannel');
    const favEl = document.getElementById('qpfav');
    if (srcEl) srcEl.textContent = srcName;
    if (chEl) chEl.textContent = curUrl ? (shown.find(i=>i.url===curUrl)?.name || 'القناة الحالية') : 'القناة';
    if (favEl) favEl.textContent = curUrl && favs.has(curUrl) ? 'مفضلة' : 'غير مفضلة';
  }
  const items=[
    {i:'📺',l:'القنوات',run:()=>{close();if(window.__rcvOpen)__rcvOpen()}},
    {i:'🛰',l:'المصادر',run:()=>{close();if(window.__rcvOpenSources)__rcvOpenSources()}},
    {i:'🔢',l:'رقم القناة',run:()=>{close();if(window.__rcvNumpad)__rcvNumpad()}},
    {i:'☆',l:'المفضلة',dyn:()=>curUrl&&favs.has(curUrl)?{i:'★',l:'إزالة من المفضلة'}:{i:'☆',l:'أضف للمفضلة'},run:()=>{close();if(curUrl){toggleFav(curUrl);if(window.__rcvToast)__rcvToast(favs.has(curUrl)?'أُضيفت إلى المفضلة':'أُزيلت من المفضلة')}}},
    {i:'🎬',l:'المكتبة',run:()=>{close();if(window.__openVod)__openVod()}},
    {i:'⚙',l:'الإعدادات',run:()=>{close();if(window.__openSettings)__openSettings()}},
    {i:'⏻',l:'خروج',run:()=>{if(window.Android&&Android.exitApp)Android.exitApp()}}
  ];
  function paint(){
    row.textContent='';
    items.forEach((it,k)=>{
      const d=it.dyn?it.dyn():it,b=document.createElement('div');b.className='qb'+(k===idx?' sel':'');
      b.innerHTML='<i></i><span></span>';b.children[0].textContent=d.i;b.children[1].textContent=d.l;
      b.onclick=()=>{idx=k;it.run()};
      row.appendChild(b);
    });
  }
  function open(){on=true;idx=0;el.classList.add('on');syncMeta();paint()}
  document.addEventListener('keydown',e=>{
    if(!on)return;
    e.preventDefault();e.stopPropagation();
    const k=e.key,rtl=getComputedStyle(document.documentElement).direction==='rtl';
    const next=rtl?'ArrowLeft':'ArrowRight',prev=rtl?'ArrowRight':'ArrowLeft';
    if(k===next)idx=Math.min(items.length-1,idx+1);
    else if(k===prev)idx=Math.max(0,idx-1);
    else if(k==='Enter'){items[idx].run();return}
    paint();
  },true);

  /* تلميح قصير للبث المباشر */
  function hintLive(){
    if(!hint)return;
    hint.textContent='↑ ↓ تغيير القناة   ·   OK قائمة القنوات   ·   رجوع: القائمة السريعة';
    hint.classList.add('on');clearTimeout(hT);hT=setTimeout(()=>hint.classList.remove('on'),6000);
  }
  window.__openQuick=open;window.__quickClose=close;window.__qpOn=()=>on;window.__hintLive=hintLive;
})();
