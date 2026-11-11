/* ===== الشاشة الافتتاحية: تبقى حتى يبدأ البث (أو 6 ثوانٍ كحد أقصى) ===== */
(function(){
  const sp=$('splash');if(!sp)return;
  const t0=performance.now();let done=false;
  const ver=$('slv');
  if(ver)ver.textContent=(window.Android&&Android.version)?('الإصدار '+Android.version()):'';
  function finish(){
    if(done)return;done=true;
    const wait=Math.max(0,1200-(performance.now()-t0));
    setTimeout(()=>{
      sp.classList.add('gone');
      setTimeout(()=>{sp.style.display='none';if(window.__hintLive)window.__hintLive()},520);
    },wait);
  }
  $('v').addEventListener('playing',finish);
  window.__splashDone=finish;
  setTimeout(finish,6000);
})();
