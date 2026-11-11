/* ===== بوابة الرجوع: خيارات سرية من المشاهدة، ضغطتان للخروج ===== */
(function(){
  if(!window.Android)return;
  const WINDOW=1500;
  let lastLiveBack=0;
  const isText=a=>a&&((a.tagName==='INPUT'&&/^(|text|search|url|number|tel|email|password)$/.test(a.type))||a.tagName==='TEXTAREA');
  const esc=()=>{const ev=new KeyboardEvent('keydown',{key:'Escape',bubbles:true,cancelable:true});document.body.dispatchEvent(ev);return ev.defaultPrevented};
  window.__back=function(){
    const now=Date.now(),a=document.activeElement;
    if(isText(a)&&!a.readOnly){a.blur();return true}
    const d=document.querySelector('dialog[open]');
    if(d){d.close();return true}
    const receiver=window.__rcvState?__rcvState():{on:false};
    const settingsOpen=!!(window.__stOn&&__stOn());
    const qpOpen=!!(window.__qpOn&&__qpOn());
    const vodOpen=!!(window.__vodOn&&__vodOn());
    // الضغط المزدوج من شاشة المشاهدة يخرج حتى لو فتحت قائمة الخيارات بالضغطة الأولى.
    if((receiver.on||settingsOpen||qpOpen)&&lastLiveBack&&now-lastLiveBack<WINDOW){lastLiveBack=0;return 'exit'}
    if(qpOpen){lastLiveBack=0;__quickClose();return true}
    if(settingsOpen){lastLiveBack=0;if(window.__stBack&&__stBack())return true}
    if(vodOpen){if(window.__vodBack&&__vodBack())return true}
    if(receiver.on){
      if(receiver.open){esc();return true}
      lastLiveBack=now;
      if(window.__openQuick)window.__openQuick();
      else if(window.__rcvOpen)__rcvOpen();
      return true;
    }
    if(document.body.classList.contains('cine-play')){esc();return true}
    if(document.body.classList.contains('cine')){window.setUIMode('rcv');return true}
    return false;
  };
})();
