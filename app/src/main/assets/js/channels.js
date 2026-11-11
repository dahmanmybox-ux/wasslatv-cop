/* ===== القنوات المدمجة من channels.json (مع الحذف والتعديل والإضافة) ===== */
(function(){
  const CHANNELS_URL='https://github.com/dzfastlink-cloud/wasslatv-downloads/releases/download/wasschn/channels.json'; // ضع هنا رابط channels.json المنشور (GitHub raw أو المدونة) لتفعيل التحديث عن بعد
  let base=null,editKey=null;
  const ov=()=>LS.get('m3u_chov',{});
  const cust=()=>LS.get('m3u_chcustom',[]);
  async function getJSON(u){
    try{
      if(window.Android&&u==='channels.json'){const t=Android.readAsset('channels.json');return t?JSON.parse(t):null}
      const ac=new AbortController(),t=setTimeout(()=>ac.abort(),8000);
      const r=await fetch(u,{cache:'no-store',signal:ac.signal});clearTimeout(t);
      return r.ok?await r.json():null;
    }catch(e){return null}
  }
  const ok=j=>j&&Array.isArray(j.channels);
  const newer=(a,b)=>!ok(b)||String(a.updated)>String(b.updated);
  async function load(){
    let best=await getJSON('channels.json');
    const cache=LS.get('m3u_chcache',null);
    if(ok(cache)&&newer(cache,best))best=cache;
    if(CHANNELS_URL){
      const rem=await getJSON(CHANNELS_URL);
      if(ok(rem)&&newer(rem,best)){best=rem;LS.set('m3u_chcache',rem)}
    }
    return ok(best)?best:null;
  }
  // القناة الفعلية = الأصل + تعديلات المستخدم
  function view(c){
    const o=ov()[c.num]||{};
    return {key:c.num,num:o.n||c.num,name:o.name!==undefined?o.name:c.name,url:o.url!==undefined?o.url:c.url,
      group:o.group!==undefined?o.group:c.group,logo:o.logo!==undefined?o.logo:(c.logo||''),
      enabled:o.enabled!==undefined?o.enabled:c.enabled!==false,deleted:!!o.deleted,flags:c.flags||[]};
  }
  const all=()=>base.channels.concat(cust()).map(view).filter(x=>!x.deleted);
  const visible=()=>all().filter(x=>x.enabled);
  function toM3U(){
    let t='#EXTM3U\n';
    visible().sort((a,b)=>a.num-b.num).forEach(x=>{
      t+='#EXTINF:-1 channel-number="'+x.num+'" ch-key="'+x.key+'"'+(x.logo?' tvg-logo="'+String(x.logo).replace(/"/g,'')+'"':'')
        +' group-title="'+String(x.group||'').replace(/"/g,'')+'",'+String(x.name).replace(/,/g,'،')+'\n'+x.url+'\n';
    });
    return t;
  }
  function info(){
    if(!base)return;
    const a=all(),en=a.filter(x=>x.enabled).length;
    $('chinfo').textContent='الإجمالي: '+a.length+' · ظاهرة: '+en+' · مخفية: '+(a.length-en)+' · آخر تحديث: '+(base.updated||'—');
  }
  function apply(reopen){
    if(!base)return;
    const l=lists.find(x=>x.id==='builtin');
    if(l){if(l.name==='📺 قنوات مدمجة')l.name='القنوات';l.text=toM3U();l.n=visible().length;l.upd=Date.now();saveLists();
      if(reopen&&active==='builtin')openList('builtin');else fillLists()}
    info();
  }
  const keyOf=it=>{const m=it&&it.raw&&/ch-key="(\d+)"/.exec(it.raw);return m?+m[1]:null};
  function setOv(key,patch){const o=ov();o[key]=Object.assign(o[key]||{},patch);LS.set('m3u_chov',o)}

  function review(){
    const box=$('chlist'),hid=all().filter(x=>!x.enabled);
    box.style.display='block';box.textContent='';
    if(!hid.length){box.textContent='لا توجد قنوات مخفية.';return}
    hid.forEach(c=>{
      const r=document.createElement('div');r.style.cssText='display:flex;gap:8px;align-items:center;padding:5px 0;border-bottom:1px solid var(--line)';
      const s=document.createElement('span');s.style.flex='1';
      s.textContent=c.num+' · '+c.name+' ('+c.group+')'+(c.flags.length?' ['+c.flags.join(', ')+']':'');
      const b=document.createElement('button');b.type='button';b.className='alt';b.textContent='اعتماد';
      b.onclick=()=>{setOv(c.key,{enabled:true});apply(true);review()};
      r.append(s,b);box.appendChild(r);
    });
  }
  window.__chHide=function(it){
    const k=keyOf(it);if(k==null||!base)return false;
    setOv(k,{enabled:false});apply(true);return true;
  };

  /* ---- نافذة التعديل ---- */
  const err=m=>{$('cemsg').textContent=m};
  function openEdit(x){
    editKey=x?x.key:null;
    $('cet').textContent=x?'تعديل قناة':'إضافة قناة';
    let num=x?x.num:'';
    if(!x){num=1;const used=new Set(all().map(v=>v.num));while(used.has(num)&&num<9999)num++}
    $('cen').value=num;$('cename').value=x?x.name:'';$('ceu').value=x?x.url:'';
    $('ceg').value=x?x.group:'';$('cel').value=x?x.logo:'';
    $('cedel').style.display=x?'':'none';err('');
    $('chedit').showModal();
  }
  window.__chEdit=function(it){
    const k=keyOf(it);if(k==null||!base)return false;
    const x=all().find(v=>v.key===k);if(!x)return false;
    openEdit(x);return true;
  };
  $('cecan').onclick=()=>$('chedit').close();
  $('cesave').onclick=()=>{
    const num=parseInt($('cen').value,10),name=$('cename').value.trim(),url=$('ceu').value.trim(),
      group=$('ceg').value.trim()||'بدون مجموعة',logo=$('cel').value.trim();
    if(!(num>=1&&num<=9999))return err('الرقم يجب أن يكون بين 1 و9999');
    if(!name)return err('اكتب اسم القناة');
    if(!/^https?:\/\//i.test(url))return err('الرابط يجب أن يبدأ بـ http أو https');
    if(all().some(v=>v.num===num&&v.key!==editKey))return err('هذا الرقم مستخدم لقناة أخرى');
    if(editKey==null){
      const cu=cust(),key=cu.reduce((m,c)=>Math.max(m,c.num),9000)+1;
      cu.push({num:key,name,url,group,logo,enabled:true});LS.set('m3u_chcustom',cu);
      setOv(key,{n:num});
    }else setOv(editKey,{n:num,name,url,group,logo});
    $('chedit').close();apply(true);
  };
  $('cedel').onclick=()=>{
    if(editKey==null)return;
    if(!confirm('حذف هذه القناة؟ (القنوات الأصلية تعود بزر «إعادة الضبط»)'))return;
    const cu=cust(),i=cu.findIndex(c=>c.num===editKey);
    if(i>=0){cu.splice(i,1);LS.set('m3u_chcustom',cu)}else setOv(editKey,{deleted:true});
    $('chedit').close();apply(true);
  };
  $('chedit').addEventListener('close',()=>{if(window.__rcvRefresh)window.__rcvRefresh()});

  // زر الفأرة الأيمن على أي قناة مدمجة
  $('list').addEventListener('contextmenu',e=>{
    const row=e.target.closest('.rw');if(!row)return;
    const idx=[...$('list').children].indexOf(row);let it=shown[idx];
    if(!it||!row.textContent.includes(it.name))it=shown.find(x=>row.textContent.includes(x.name));
    if(it&&keyOf(it)!=null){e.preventDefault();window.__chEdit(it)}
  });

  /* ---- أزرار الإعدادات ---- */
  const nobase=()=>{$('chinfo').textContent='تعذّر تحميل channels.json'};
  $('chadd').onclick=async()=>{
    base=await load();if(!base)return nobase();
    if(!lists.find(x=>x.id==='builtin'))lists.unshift({id:'builtin',name:'القنوات',text:'',url:'',n:0,upd:Date.now()});
    apply(false);openList('builtin');$('dlg').close();
  };
  $('chrev').onclick=async()=>{if(!base)base=await load();if(!base)return nobase();info();review()};
  $('chnew').onclick=async()=>{if(!base)base=await load();if(!base)return nobase();$('dlg').close();openEdit(null)};
  $('chrst').onclick=async()=>{
    if(!base)base=await load();if(!base)return nobase();
    LS.del('m3u_chov');
    cust().forEach(c=>{}); // القنوات التي أضفتها أنت تبقى
    apply(true);info();if($('chlist').style.display!=='none')review();
  };
  window.__chReload=async function(){base=await load();if(base)apply(true)};
  window.__chKey=keyOf;
  window.__chSet=function(key,patch){if(!base)return false;setOv(key,patch);apply(true);return true};
  let tries=0;
  const w=setInterval(async()=>{
    tries++;
    if(lists.length||tries>25){
      clearInterval(w);
      base=await load();
      if(base){
        // قائمة مدمجة جديدة: تعديلات الأرقام القديمة لا تنطبق عليها، فتُصفَّر مرة واحدة (قنوات المستخدم المضافة تبقى)
        const sg=String(base.version)+'|'+String(base.updated);
        if(LS.get('m3u_chsig','')!==sg){LS.del('m3u_chov');LS.set('m3u_chsig',sg)}
        apply(true);
      }
      // البدء من آخر قناة، وإلا من أول قناة
      setTimeout(()=>{if(!curUrl&&shown.length&&document.body.classList.contains('rcv'))play(shown[0])},900);
    }
  },300);
})();
