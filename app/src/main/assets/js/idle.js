/* ===== شاشة السكون: تظهر خلفية العلامة ما دام لا يوجد بث يعمل ===== */
(function(){
  const B=document.body,v=$('v');
  B.classList.add('is-idle');
  v.addEventListener('playing',()=>B.classList.remove('is-idle'));
  ['emptied','error','ended'].forEach(e=>v.addEventListener(e,()=>B.classList.add('is-idle')));
  const _p=play;
  play=function(it){
    if(!(it&&it.url===curUrl&&!v.paused))B.classList.add('is-idle');
    return _p.apply(this,arguments);
  };
})();
