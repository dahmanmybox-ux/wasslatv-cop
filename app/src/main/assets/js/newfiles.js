/* ===== العثور على ملفات القنوات الجديدة: عند الفتح (تلقائياً) أو من الإعدادات ===== */
(function(){
  if(!window.Android||!Android.scanNew)return;
  const SEEN='m3u_seen';
  const sig=f=>f.name+'|'+f.size+'|'+f.mod;
  const base=n=>n.replace(/\.[^.]+$/,'');
  const seenSet=()=>new Set(LS.get(SEEN,[]));
  function markSeen(f){const s=[...seenSet()];s.push(sig(f));LS.set(SEEN,s.slice(-300))}

  function askPerm(){
    return new Promise(res=>{
      window.__onPerm=()=>{window.__onPerm=null;res()};
      Android.askPerm();
      setTimeout(()=>{if(window.__onPerm){window.__onPerm=null;res()}},20000);
    });
  }
  /* يعيد الملفات التي لم تُضَف ولم تُتجاهل من قبل */
  window.__scanFiles=async function(){
    try{ if(Android.needsPerm())await askPerm() }catch(e){}
    let arr=[];
    try{arr=JSON.parse(Android.scanNew())}catch(e){}
    const s=seenSet();
    return arr.filter(f=>!s.has(sig(f)));
  };
  window.__importFile=async function(f,askName){
    const txt=Android.readFile(f.path);
    if(!txt)return false;
    let nm=base(f.name);
    if(askName&&window.kbPrompt){const r=await kbPrompt('اسم هذا المصدر',nm);if(r===null)return false;nm=r.trim()||nm}
    const ok=await addList(txt,nm,'');
    if(ok)markSeen(f);
    return ok;
  };
  window.__ignoreFile=markSeen;

  /* نافذة التنبيه عند الإقلاع */
  const dlg=$('nf'),txt=$('nft'),addB=$('nfadd'),skipB=$('nfskip'),laterB=$('nflater');
  let queue=[],curF=null;
  function next(){
    curF=queue.shift();
    if(!curF){if(dlg.open)dlg.close();return}
    txt.textContent='ملف قنوات جديد: «'+curF.name+'» ('+Math.round(curF.size/1024)+' ك.ب). هل تريد إضافته؟';
    if(!dlg.open)dlg.showModal();
    addB.focus();
  }
  addB.onclick=async()=>{const f=curF;dlg.close();await window.__importFile(f,false);next()};
  skipB.onclick=()=>{markSeen(curF);next()};
  laterB.onclick=()=>{queue=[];dlg.close()};

  // فحص تلقائي مرة عند الإقلاع بعد جاهزية القوائم
  let tries=0;
  const w=setInterval(async()=>{
    tries++;
    if(lists.length||tries>25){
      clearInterval(w);
      if(!LS.get('m3u_autoscan',true))return;
      setTimeout(async()=>{
        try{
          if(Android.needsPerm())return;            // لا نزعج المستخدم بطلب صلاحية عند الإقلاع
          const files=await window.__scanFiles();
          if(files.length){queue=files.slice(0,5);next()}
        }catch(e){}
      },2500);
    }
  },400);
})();
