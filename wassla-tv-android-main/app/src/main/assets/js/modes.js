/* ===== النمط الوحيد: الريسيفر المباشر + صوت التنقل (أنماط TiviMate وSmarters والكلاسيكي حُذفت) ===== */
(function(){
  const zs=$('zapsnd');let ac=null;
  function beep(){
    try{
      ac=ac||new(window.AudioContext||window.webkitAudioContext)();
      const o=ac.createOscillator(),g=ac.createGain(),t=ac.currentTime;
      o.type='sine';o.frequency.value=1400;
      g.gain.setValueAtTime(.0001,t);g.gain.exponentialRampToValueAtTime(.12,t+.01);g.gain.exponentialRampToValueAtTime(.0001,t+.09);
      o.connect(g);g.connect(ac.destination);o.start(t);o.stop(t+.1);
    }catch(e){}
  }
  window.setUIMode=function(m,silent){window.__rcv.set(true,silent);LS.set('m3u_mode','rcv')};
  zs.checked=LS.get('m3u_zap',true);zs.onchange=e=>LS.set('m3u_zap',e.target.checked);
  const _p=play;
  play=function(it){_p(it);if(zs.checked&&!window.__previewing&&performance.now()>4000)beep()};
  setTimeout(()=>window.setUIMode('rcv',true),300);
})();
