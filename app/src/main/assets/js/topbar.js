/* ===== الشريط العلوي (أندرويد): أيقونات منسّقة بتنقل أفقي ===== */
(function(){
  const bar=$('tbar');if(!bar)return;
  const modes=[...bar.querySelectorAll('button[data-m]')],msg=bar.querySelector('.tmsg'),favB=$('tfav');
  const say=t=>{msg.textContent=t||''};
  const mark=m=>modes.forEach(b=>b.classList.toggle('on',b.dataset.m===m));
  modes.forEach(b=>b.onclick=()=>window.setUIMode(b.dataset.m));
  const _s=window.setUIMode;
  if(_s)window.setUIMode=function(m){mark(m);return _s.apply(this,arguments)};
  favB.onclick=()=>{
    const g=$('grp');g.value=(g.value==='__fav')?'':'__fav';render();
    favB.classList.toggle('on',g.value==='__fav');
  };
  $('tset').onclick=()=>openSet();
  $('tref').onclick=async()=>{
    say('جارٍ التحديث…');
    try{
      if(active==='builtin'&&window.__chReload)await window.__chReload();
      else $('upd').click();
      say('تم التحديث ✓');
    }catch(e){say('تعذّر التحديث')}
    setTimeout(()=>say(''),2500);
  };
})();
