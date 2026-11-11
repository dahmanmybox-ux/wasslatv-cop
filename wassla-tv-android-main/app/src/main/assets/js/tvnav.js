/* ===== تنقل الريموت على التلفاز =====
   • الإعدادات والنوافذ: ↑ ↓ بالترتيب خانة خانة (سلس)، و← → داخل الصف نفسه.
   • لوحة المفاتيح (data-nav="grid"): تنقل مكاني بالأسهم الأربعة.
   • الصفحات الأخرى (سينما): تنقل مكاني بلا قفز بين الأعمدة أو الصفوف. */
(function(){
  if(!window.Android||!Android.isTv())return;
  const B=document.body;
  const SEL='button:not([disabled]),a[href],input:not([type=hidden]),select,textarea,[tabindex]:not([tabindex="-1"])';
  const isText=a=>a&&((a.tagName==='INPUT'&&/^(|text|search|url|number|tel|email|password)$/.test(a.type))||a.tagName==='TEXTAREA');
  const stRoot=()=>{const s=document.getElementById('st');return s&&s.classList.contains('on')?s:null};
  const root=()=>document.querySelector('dialog[open]')||stRoot();     // null = الصفحة كلها

  const vis=e=>{
    if(e.disabled||e.classList.contains('st'))return false;
    const r=e.getClientRects();
    if(!r.length||r[0].width<4||r[0].height<4)return false;
    return getComputedStyle(e).visibility!=='hidden';
  };
  const f=t=>{t.focus({preventScroll:true});t.scrollIntoView({block:'nearest',inline:'nearest'})};

  /* أقرب عنصر في الاتجاه المطلوب دون قفز بين الأعمدة أو الصفوف */
  function pick(dir,cur,els){
    const c=cur.getBoundingClientRect(),vert=(dir==='ArrowUp'||dir==='ArrowDown');
    const cx=(c.left+c.right)/2,cy=(c.top+c.bottom)/2;
    let best=null,bs=Infinity,loose=null,ls=Infinity;
    for(const e of els){
      if(e===cur||cur.contains(e)||e.contains(cur))continue;
      const r=e.getBoundingClientRect(),dx=(r.left+r.right)/2-cx,dy=(r.top+r.bottom)/2-cy;
      let p,q,ov;
      if(vert){
        if(dir==='ArrowDown'){if(dy<=4)continue;p=dy}else{if(dy>=-4)continue;p=-dy}
        q=Math.abs(dx);ov=Math.min(c.right,r.right)-Math.max(c.left,r.left);
        if(ov<=0&&q>Math.max(c.width,r.width))continue;
        const s=p+q*3;if(s<bs){bs=s;best=e}
      }else{
        if(dir==='ArrowRight'){if(dx<=4)continue;p=dx}else{if(dx>=-4)continue;p=-dx}
        q=Math.abs(dy);ov=Math.min(c.bottom,r.bottom)-Math.max(c.top,r.top);
        const s=p+q*3;
        if(ov>0){if(s<bs){bs=s;best=e}}
        else if(p>c.width*0.6&&s<ls){ls=s;loose=e}
      }
    }
    return best||loose;
  }

  function go(dir){
    const a=document.activeElement,r=root();
    let all=[...(r||document).querySelectorAll(SEL)].filter(vis);
    if(a&&r&&r.id==='st'&&(dir==='ArrowUp'||dir==='ArrowDown')){const nv=a.closest('#stnav');all=all.filter(e=>!!e.closest('#stnav')===!!nv)}
    if(!a||a===B||!all.includes(a)){
      const t=all.find(e=>e.classList.contains('ch'))||all[0];
      if(t)f(t);return;
    }
    if(r&&r.dataset.nav!=='grid'&&(dir==='ArrowUp'||dir==='ArrowDown')){
      const i=all.indexOf(a),st=dir==='ArrowDown'?1:-1,ar=a.getBoundingClientRect();
      for(let j=i+st;j>=0&&j<all.length;j+=st){
        const q=all[j].getBoundingClientRect();
        if(Math.abs((q.top+q.bottom)/2-(ar.top+ar.bottom)/2)>ar.height*0.6){f(all[j]);return}
      }
      return;
    }
    const t=pick(dir,a,all);
    if(t)f(t);
  }

  /* لوحة مفاتيح النظام لا تظهر: خانات الكتابة تُقفل، وOK يفتح لوحة التطبيق */
  function lock(el){
    if(!isText(el))return;
    if(!el.dataset.im)el.dataset.im=el.getAttribute('inputmode')||'text';
    el.readOnly=true;el.setAttribute('inputmode','none');
  }
  document.querySelectorAll('input,textarea').forEach(lock);
  document.addEventListener('focusout',e=>lock(e.target),true);

  document.addEventListener('keydown',e=>{
    if(window.__vodOn&&window.__vodOn())return;
    const r=root();
    if((B.classList.contains('rcv')&&!r)||B.classList.contains('cine-play'))return;
    if(e.ctrlKey||e.altKey||e.metaKey)return;
    const a=document.activeElement,k=e.key;
    if(isText(a)&&!a.readOnly){
      if(k==='Escape'||(k==='Enter'&&a.tagName==='INPUT')){e.preventDefault();a.blur()}
      return;
    }
    if(k==='Enter'&&isText(a)&&a.readOnly){
      e.preventDefault();e.stopPropagation();
      if(window.kbEdit)window.kbEdit(a);
      else{a.readOnly=false;a.setAttribute('inputmode',a.dataset.im||'text');a.blur();a.focus();Android.showKeyboard()}
      return;
    }
    if(/^Arrow(Up|Down|Left|Right)$/.test(k)){e.preventDefault();e.stopPropagation();go(k);return}
    // داخل الإعدادات والنوافذ: لا تُسرَّب الأرقام والحروف إلى باقي التطبيق
    if(r&&!/^(Enter|Escape|Backspace|Tab)$/.test(k))e.stopPropagation();
  },true);
})();
