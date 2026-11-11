/* ===== غلاف أندرويد: الرجوع، تتبّع النمط، OK على القناة العاملة ===== */
(function(){
  if(!window.Android)return;
  const B=document.body,tv=Android.isTv();
  B.classList.add('android');
  if(tv)B.classList.add('tv');

  // تتبّع النمط السابق: للعودة من الريسيفر بزر الرجوع
  let cur=null;
  const _s=window.setUIMode;
  if(_s)window.setUIMode=function(m){
    if(m==='rcv'&&cur&&cur!=='rcv')window.__prevMode=cur;
    cur=m;return _s.apply(this,arguments);
  };
  window.__zap=function(d){if(window.__caps){__caps.ch=true}if(window.__zapNum&&document.body.classList.contains('rcv'))__zapNum(d);else if(window.step)step(d)};

  const isText=a=>a&&((a.tagName==='INPUT'&&/^(|text|search|url|number|tel|email|password)$/.test(a.type))||a.tagName==='TEXTAREA');

  /* OK على القناة التي تعمل = ملء الشاشة (وضع الريسيفر) */
  const _p=window.play;
  if(tv&&_p)window.play=function(it){
    const same=it&&it.url===curUrl&&!$('v').paused&&!B.classList.contains('rcv');
    if(same&&cur&&cur!=='rcv'&&cur!=='cine'){window.setUIMode('rcv');return}
    return _p.apply(this,arguments);
  };
})();
