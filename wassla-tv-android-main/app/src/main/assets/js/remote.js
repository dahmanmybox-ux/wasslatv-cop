/* ===== ريموت تكيفي: يتجاهل تكرار OK ويتعرّف على الأزرار المتاحة ===== */
(function(){
  const caps=LS.get('m3u_caps',{digits:false,color:false,ch:false});
  window.__caps=caps;
  const fire=()=>{LS.set('m3u_caps',caps);document.dispatchEvent(new Event('caps'))};
  document.addEventListener('keydown',e=>{
    // الضغط المطوّل على OK يولّد تكراراً: نعدّه ضغطة واحدة فقط
    if((e.key==='Enter'||e.key===' ')&&e.repeat){e.preventDefault();e.stopImmediatePropagation();return}
    if(/^[0-9]$/.test(e.key)&&!caps.digits){caps.digits=true;fire()}
    else if(/^F[1-4]$/.test(e.key)&&!caps.color){caps.color=true;fire()}
  },true);
})();
