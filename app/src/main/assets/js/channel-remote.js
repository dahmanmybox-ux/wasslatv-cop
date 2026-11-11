/* ===== المشغّل: استجابة أزرار الريموت على ترتيب أرقام القنوات ===== */
(function(){
  const channelNumber=(it,index)=>{
    const match=it&&it.raw&&/channel-number="(\d+)"/.exec(it.raw);
    const n=match?Number(match[1]):0;
    return n>0?n:index+1;
  };
  const nextByNumber=direction=>{
    if(!shown.length)return;
    const currentIndex=shown.findIndex(it=>it.url===curUrl);
    const current=currentIndex<0?0:currentIndex;
    const currentNumber=channelNumber(shown[current],current);
    const candidates=shown.map((it,index)=>({it,index,number:channelNumber(it,index)}));
    const ordered=direction>0
      ? candidates.filter(x=>x.number>currentNumber).sort((a,b)=>a.number-b.number)
      : candidates.filter(x=>x.number<currentNumber).sort((a,b)=>b.number-a.number);
    const target=ordered[0]|| (direction>0
      ? candidates.slice().sort((a,b)=>a.number-b.number)[0]
      : candidates.slice().sort((a,b)=>b.number-a.number)[0]);
    if(target)play(target.it);
  };
  document.addEventListener('keydown',e=>{
    if(e.defaultPrevented||document.querySelector('dialog[open]')||window.__stOn&&window.__stOn()||window.__vodOn&&window.__vodOn())return;
    if(/INPUT|TEXTAREA|SELECT/.test(e.target.tagName)||e.ctrlKey||e.altKey||e.metaKey)return;
    const key=e.key.toLowerCase();
    if(document.body.classList.contains('rcv'))return; // receiver.js owns live-view keys
    if(key==='arrowup'){e.preventDefault();nextByNumber(1)}
    else if(key==='arrowdown'){e.preventDefault();nextByNumber(-1)}
  });
})();
