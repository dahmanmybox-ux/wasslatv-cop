/* ===== الإعدادات: قائمة خدمات، لكل خدمة شاشتها، والتنقل بالأسهم وOK ورجوع =====
   المصادر · إضافة مصدر · تعديل القنوات · البحث عن ملفات جديدة · مكتبة الأفلام · حول */
(function(){
  const st=$('st'),body=$('stb'),title=$('stt'),msg=$('stm'),A=window.Android;
  let stack=[],tt;
  const toast=t=>{msg.textContent=t||'';clearTimeout(tt);tt=setTimeout(()=>{msg.textContent=''},4000)};
  const user=()=>lists.filter(l=>l.id!==FAV_LIST_ID);
  const rowsEls=()=>[...body.querySelectorAll('.srow')];
  const focusIdx=()=>Math.max(0,rowsEls().indexOf(document.activeElement));

  /* ---------------- معاينة البث الحي أثناء التعديل ---------------- */
  const pvBox=$('stpv');
  let pvT=null,pvOn=false,pvUrl='',liveWas=null,wantPv=false;
  function placeV(){
    const r=pvBox.getBoundingClientRect(),s=$('v').style;
    s.position='fixed';s.left=r.left+'px';s.top=r.top+'px';s.width=r.width+'px';s.height=r.height+'px';s.zIndex='96';
  }
  function pvStart(){
    if(pvOn)return;
    pvOn=true;window.__previewing=true;
    liveWas=shown.find(i=>i.url===curUrl)||null;
    st.classList.add('pv');document.body.classList.add('stprev');
    placeV();
  }
  function pvPlay(it,now){
    if(!it||pvUrl===it.url)return;
    pvStart();clearTimeout(pvT);
    pvT=setTimeout(()=>{
      pvUrl=it.url;
      const sR=recs.slice(),sL=LS.get('m3u_last',null);       // المعاينة لا تغيّر «آخر قناة» ولا المشاهَدة مؤخراً
      play(it);
      recs=sR;LS.set('m3u_recs',sR);
      if(sL)LS.set('m3u_last',sL);
    },now?0:600);
  }
  function pvStop(){
    if(!pvOn)return;
    clearTimeout(pvT);pvOn=false;pvUrl='';
    st.classList.remove('pv');document.body.classList.remove('stprev');
    $('v').style.cssText='';window.__previewing=false;
    tok++;stopAll();
    if(liveWas&&document.body.classList.contains('rcv')){const it=liveWas;liveWas=null;play(it)}
  }
  window.addEventListener('resize',()=>{if(pvOn)placeV()});

  let sec='general',nf=false;
  function show(t,rows,keep){
    if(!wantPv)pvStop();wantPv=false;
    title.textContent=t;body.textContent='';
    rows.forEach(r=>{
      if(r.sep!==undefined){const d=document.createElement('div');d.className='sep';d.textContent=r.sep;body.appendChild(d);return}
      const b=document.createElement('button');b.type='button';b.className='srow'+(r.danger?' danger':'');
      b.innerHTML='<i></i><span class="sx"><b class="sl"></b><small class="ss"></small></span><em class="sc"></em>';
      b.children[0].textContent=r.icon||'';b.querySelector('.sl').textContent=r.label;b.querySelector('.ss').textContent=r.sub||'';
      const c=b.querySelector('.sc');
      if(r.on!==undefined){c.className='sc sw'+(r.on?' on':'');b.setAttribute('role','switch');b.setAttribute('aria-checked',r.on?'true':'false')}
      else if(r.val!==undefined)c.textContent=r.val+' ▾';
      else c.remove();
      b.onclick=()=>{if(r.run)r.run()};
      if(r.onfocus)b.addEventListener('focus',r.onfocus);
      body.appendChild(b);
    });
    navMark();
    const f=rowsEls()[keep||0];
    if(f&&!nf)f.focus({preventScroll:true});
    body.scrollTop=0;
    if(f&&!nf)f.scrollIntoView({block:'nearest'});
  }
  const SECS=[
    {id:'general',icon:'⚙',t:'عام',fn:general},
    {id:'sources',icon:'📂',t:'المصادر',fn:sources},
    {id:'channels',icon:'✎',t:'القنوات',fn:channelsSec},
    {id:'library',icon:'🎬',t:'المكتبة',fn:librarySec},
    {id:'backup',icon:'☁',t:'نسخ احتياطي',fn:backupSec},
    {id:'reset',icon:'↺',t:'إعادة تعيين',fn:resetSec},
    {id:'about',icon:'ℹ',t:'حول',fn:about}
  ];
  const navEl=$('stnav');
  SECS.forEach(x=>{
    const b=document.createElement('button');b.type='button';b.dataset.s=x.id;
    b.innerHTML='<i></i><span></span>';b.children[0].textContent=x.icon;b.children[1].textContent=x.t;
    const go=()=>{if(sec===x.id&&stack.length===1)return;sec=x.id;stack=[home];nf=true;try{home()}finally{nf=false}};
    b.addEventListener('focus',go);
    b.onclick=()=>{go();const f=rowsEls()[0];if(f)f.focus({preventScroll:true})};
    navEl.appendChild(b);
  });
  function navMark(){navEl.querySelectorAll('button').forEach(b=>b.classList.toggle('on',b.dataset.s===sec))}
  function push(fn){
    const cur=stack[stack.length-1];if(cur)cur.keep=focusIdx();
    stack.push(fn);fn();
  }
  function back(){
    stack.pop();
    const f=stack[stack.length-1];
    if(f)f(f.keep);else close();
  }
  function rerender(){const f=stack[stack.length-1];if(f){f.keep=focusIdx();f(f.keep)}}
  function setTop(fn,keep){stack[stack.length-1]=fn;fn(keep)}
  function open(which){st.classList.add('on');sec=which==='sources'?'sources':'general';stack=[home];home()}
  function close(){pvStop();st.classList.remove('on');stack=[];if(document.activeElement)document.activeElement.blur()}
  window.__openSettings=open;
  window.__stOn=()=>st.classList.contains('on');
  window.__stToast=toast;
  window.__stBack=function(){if(!st.classList.contains('on'))return false;back();return true};
  $('stback').onclick=()=>back();

  /* ---------------- الرئيسية ---------------- */
  function home(keep){(SECS.find(x=>x.id===sec)||SECS[0]).fn(keep)}
  function general(keep){
    const auto=LS.get('m3u_autoscan',true),zap=$('zapsnd').checked;
    show('عام',[
      {icon:'🎨',label:'السمة البصرية',sub:'تلقائي · نهاري · ليلي',val:(MTXT[theme]||'').replace(/^\S+\s/,''),run:()=>{cycleTheme();rerender()}},
      {icon:'🔊',label:'صوت التنقل بين القنوات',sub:'نغمة قصيرة عند تغيير القناة',on:zap,run:()=>{$('zapsnd').checked=!zap;LS.set('m3u_zap',!zap);rerender()}},
      {icon:'🔎',label:'البحث التلقائي عن الملفات',sub:'عند فتح التطبيق: التنزيلات · الوثائق · USB',on:auto,run:()=>{LS.set('m3u_autoscan',!auto);rerender()}},
      {icon:'📂',label:'اختيار مجلد للبحث فيه',sub:'للأجهزة التي تمنع الوصول المباشر',run:()=>{
        if(A&&A.pickFolder){window.__onFolder=ok=>{window.__onFolder=null;toast(ok?'تم حفظ المجلد':'')};A.pickFolder()}else toast('غير متاح على هذا الجهاز');
      }}
    ],keep);
  }
  function channelsSec(keep){
    show('القنوات',[
      {icon:'✎',label:'تعديل القنوات',sub:'الاسم · الرابط · المجموعة · الرقم · الترتيب · الحذف',run:()=>push(editorPick)},
      {icon:'🔎',label:'البحث عن ملفات جديدة',sub:'قوائم M3U جديدة على الجهاز',run:()=>push(scanScreen)}
    ],keep);
  }
  function librarySec(keep){
    show('المكتبة',[{icon:'🎬',label:'مكتبة الأفلام والمسلسلات',sub:'من حسابات Xtream المضافة',run:()=>{close();window.__openVod()}}],keep);
  }
  function backupSec(keep){
    const rows=user().map(l=>({icon:'💾',label:'تصدير «'+l.name+'»',sub:(l.n||0)+' قناة · ملف M3U في مجلد التطبيق',run:()=>{
      const nm='wassla-backup-'+l.name+'-'+new Date().toISOString().slice(0,10)+'.m3u';
      const p=(A&&A.saveFile)?A.saveFile(nm,l.text||''):'';toast(p?'حُفظت النسخة: '+p:'تعذّر الحفظ');
    }}));
    rows.push({sep:'الاستيراد'},{icon:'📁',label:'استيراد من ملف',sub:'استرجاع نسخة احتياطية',run:pickLocal});
    show('نسخ احتياطي',rows,keep);
  }
  function resetSec(keep){
    const u=user().filter(l=>l.id!=='builtin');
    show('إعادة تعيين',[
      {icon:'📄',label:'قوائم M3U',sub:String(u.filter(l=>!l.xt).length),run:()=>{}},
      {icon:'☁',label:'حسابات Xtream',sub:String(u.filter(l=>l.xt).length),run:()=>{}},
      {icon:'🗑',label:'حذف كل المصادر المضافة',sub:'القائمة المدمجة تبقى كما هي',danger:true,run:()=>push(confirmAll)}
    ],keep);
  }
  function confirmAll(){
    show('تأكيد الحذف',[
      {icon:'🗑',label:'نعم، احذف كل المصادر المضافة نهائياً',danger:true,run:async()=>{
        for(const l of user().filter(x=>x.id!=='builtin'))await deleteList(l.id);
        toast('تم الحذف');stack=[home];home();
      }},
      {icon:'↩',label:'إلغاء',run:()=>back()}
    ],1);
  }

  /* ---------------- المصادر ---------------- */
  function sources(keep){
    const rows=user().map(l=>({
      icon:l.id===active?'✔':'📄',label:l.name,
      sub:(l.n||0)+' قناة'+(l.url?' · من رابط':''),
      run:()=>push(k=>source(l.id,k))
    }));
    rows.push({sep:''},{icon:'➕',label:'إضافة مصدر جديد',sub:'ملف · رابط M3U · Xtream',run:()=>push(addScreen)});
    show('المصادر',rows,keep);
  }
  function source(id,keep){
    const l=lists.find(x=>x.id===id);if(!l){back();return}
    const rows=[
      {icon:'▶',label:'استعمال هذا المصدر',sub:l.id===active?'مفعّل الآن':'',run:()=>{openList(id);toast('تم تفعيل «'+l.name+'»');rerender()}},
      {icon:'✎',label:'إعادة التسمية',sub:l.name,run:async()=>{
        const v=await kbPrompt('اسم المصدر',l.name);
        if(v&&v.trim()){l.name=v.trim();await saveLists();fillLists();rerender()}
      }}
    ];
    if(l.xt)rows.push({icon:'⟳',label:'تحديث من Xtream',sub:l.xt.host,run:async()=>{toast('جارٍ التحديث…');const ok=await window.xtRefresh(l);toast(ok?'تم التحديث':'تعذّر التحديث');rerender()}});
    if(l.url)rows.push({icon:'⟳',label:'تحديث من الرابط',sub:l.url,run:async()=>{toast('جارٍ التحديث…');await updateList(id);toast('انتهى التحديث');rerender()}});
    rows.push({icon:'✎',label:'تعديل قنوات هذا المصدر',run:()=>push(k=>editor(id,0,k))});
    if(id!=='builtin')rows.push({icon:'🗑',label:'حذف هذا المصدر',danger:true,run:()=>push(k=>confirmDel(id,k))});
    show('المصدر: '+l.name,rows,keep);
  }
  function confirmDel(id){
    const l=lists.find(x=>x.id===id);if(!l){back();return}
    show('تأكيد الحذف',[
      {icon:'🗑',label:'نعم، احذف «'+l.name+'» نهائياً',danger:true,run:async()=>{await deleteList(id);toast('تم الحذف');stack.pop();back()}},
      {icon:'↩',label:'إلغاء',run:()=>back()}
    ],1);
  }

  /* ---------------- إضافة مصدر ---------------- */
  function addScreen(keep){
    show('إضافة مصدر',[
      {icon:'📁',label:'من ملف على الجهاز',sub:'منتقي الملفات',run:pickLocal},
      {icon:'🔗',label:'من رابط M3U',sub:'يُنزَّل ويُحفظ باسم تختاره',run:addUrl},
      {icon:'🔑',label:'حساب Xtream',sub:'السيرفر · المستخدم · كلمة السر',run:addXtream},
      {icon:'🔎',label:'بحث تلقائي عن الملفات',run:()=>push(scanScreen)}
    ],keep);
  }
  function pickLocal(){
    if(!A||!A.pickFile){toast('اختيار الملفات غير متاح هنا');return}
    window.__onPicked=async(ok,why)=>{
      window.__onPicked=null;
      if(!ok){toast(why==='nohandler'?'لا يوجد تطبيق ملفات على هذا الجهاز — استعمل الرابط أو «البحث التلقائي»':(why==='cancel'?'':'تعذّرت قراءة الملف'));return}
      const name=A.pickedName(),text=A.pickedText(),base=name.replace(/\.[^.]+$/,'');
      const nm=await kbPrompt('اسم هذا المصدر',base);
      if(nm===null)return;
      toast('جارٍ إضافة المصدر…');
      const added=await addList(text,nm.trim()||base,'');
      toast(added?'تمت إضافة «'+(nm.trim()||base)+'»':'الملف لا يحتوي قنوات صالحة');
      if(added){stack=[home];home()}
    };
    A.pickFile();
  }
  async function addUrl(){
    const u=((await kbPrompt('رابط القائمة (M3U أو M3U8)','http://','en'))||'').trim();
    if(!/^https?:\/\//i.test(u)){if(u)toast('الرابط يجب أن يبدأ بـ http أو https');return}
    toast('جارٍ التنزيل…');
    let text='';
    try{text=await getText(u)}catch(e){toast('تعذّر التنزيل من هذا الرابط');return}
    const host=(u.match(/^https?:\/\/([^\/:?#]+)/)||[])[1]||'قائمة';
    const nm=await kbPrompt('اسم هذا المصدر',host);
    if(nm===null)return;
    const ok=await addList(text,nm.trim()||host,u);
    toast(ok?'تمت الإضافة':'لا توجد قنوات صالحة في هذا الرابط');
    if(ok){stack=[home];home()}
  }
  async function addXtream(){
    const host=((await kbPrompt('عنوان السيرفر','http://','en'))||'').trim();if(!host)return;
    const user_=await kbPrompt('اسم المستخدم','','en');if(user_===null)return;
    const pass=await kbPrompt('كلمة السر','','en');if(pass===null)return;
    const nm=await kbPrompt('اسم هذا المصدر','Xtream');if(nm===null)return;
    toast('جارٍ الاتصال بالسيرفر… (قد يستغرق لحظات)');
    const ok=await window.xtAdd(host,user_.trim(),pass.trim(),nm.trim()||'Xtream');
    toast(ok?'تمت إضافة المصدر: قنوات وأفلام ومسلسلات':'تعذّر الاتصال أو لا توجد قنوات — تحقق من العنوان والبيانات');
    if(ok){stack=[home];home()}
  }

  /* ---------------- البحث عن ملفات جديدة ---------------- */
  function scanScreen(keep){
    const auto=LS.get('m3u_autoscan',true);
    show('البحث عن ملفات جديدة',[
      {icon:'🔎',label:'ابحث الآن',sub:'التنزيلات · الوثائق · USB · مجلد التطبيق',run:runScan},
      {icon:'📂',label:'اختيار مجلد للبحث فيه',sub:'للأجهزة التي تمنع الوصول المباشر',run:()=>{
        if(A&&A.pickFolder){window.__onFolder=ok=>{window.__onFolder=null;toast(ok?'تم حفظ المجلد — اضغط «ابحث الآن»':'')};A.pickFolder()}
        else toast('غير متاح على هذا الجهاز');
      }},
      {icon:auto?'☑':'☐',label:'البحث التلقائي عند فتح التطبيق',sub:auto?'مفعّل':'معطّل',run:()=>{LS.set('m3u_autoscan',!auto);rerender()}},
      {sep:'مجلد التطبيق — انسخ ملفاتك إليه بالكمبيوتر أو USB:'},
      {icon:'📍',label:(A&&A.appDir)?A.appDir():'—',run:()=>{}}
    ],keep);
  }
  async function runScan(){
    toast('جارٍ البحث…');
    const files=window.__scanFiles?await window.__scanFiles():[];
    if(!files.length){toast('لا توجد ملفات جديدة');return}
    push(k=>found(files,k));
  }
  function found(files,keep){
    const rows=files.map(f=>({icon:'📄',label:f.name,sub:Math.round(f.size/1024)+' ك.ب',run:async()=>{
      const ok=await window.__importFile(f,true);
      if(ok){
        toast('تمت إضافة «'+f.name+'»');
        files.splice(files.indexOf(f),1);
        if(files.length)rerender();else back();
      }else toast('لم تتم الإضافة');
    }}));
    rows.push({sep:''},{icon:'🙈',label:'تجاهل هذه الملفات',run:()=>{files.forEach(f=>window.__ignoreFile(f));toast('سيتم تجاهلها');back()}});
    show('ملفات جديدة ('+files.length+')',rows,keep);
  }

  /* ---------------- تعديل القنوات ---------------- */
  const PAGE=80;
  const numOfRaw=(it,i)=>{const m=it.raw&&/channel-number="(\d+)"/.exec(it.raw);return m?+m[1]:i+1};
  const itemsOf=id=>{const l=lists.find(x=>x.id===id);return l?parseM3U(l.text):[]};
  function editorPick(keep){
    show('تعديل القنوات — اختر المصدر',user().map(l=>({
      icon:l.id===active?'✔':'📄',label:l.name,sub:(l.n||0)+' قناة',run:()=>push(k=>editor(l.id,0,k))
    })),keep);
  }
  function editor(id,page,keep){
    const l=lists.find(x=>x.id===id);if(!l){back();return}
    const its=itemsOf(id),pages=Math.max(1,Math.ceil(its.length/PAGE));
    page=Math.min(Math.max(0,page),pages-1);
    const rows=[{icon:'💾',label:'حفظ نسخة احتياطية بهذا الترتيب',sub:'ملف M3U في مجلد التطبيق',run:()=>{
      const nm='wassla-backup-'+l.name+'-'+new Date().toISOString().slice(0,10)+'.m3u';
      const p=(A&&A.saveFile)?A.saveFile(nm,ser(its)):'';
      toast(p?'حُفظت النسخة: '+p:'تعذّر الحفظ');
    }}];
    if(page>0)rows.push({icon:'◀',label:'الصفحة السابقة',sub:(page)+' / '+pages,run:()=>setTop(k=>editor(id,page-1,k),1)});
    const first=rows.length;
    its.slice(page*PAGE,page*PAGE+PAGE).forEach((it,j)=>{
      const i=page*PAGE+j;
      rows.push({icon:String(numOfRaw(it,i)),label:it.name,sub:it.group||'',run:()=>push(k=>chan(id,i,k)),onfocus:()=>pvPlay(it)});
    });
    if(page<pages-1)rows.push({icon:'▶',label:'الصفحة التالية',sub:(page+2)+' / '+pages,run:()=>setTop(k=>editor(id,page+1,k),1)});
    wantPv=true;
    show(l.name+' — '+its.length+' قناة'+(pages>1?' (صفحة '+(page+1)+'/'+pages+')':''),rows,keep!=null?keep:first);
  }
  function chan(id,idx,keep){
    const l=lists.find(x=>x.id===id);if(!l){back();return}
    const its=parseM3U(l.text),it=its[idx];if(!it){back();return}
    const isB=id==='builtin'&&window.__chKey&&window.__chKey(it)!=null;
    const rows=[
      {icon:'✎',label:'الاسم',sub:it.name,run:()=>editField(id,idx,'name')},
      {icon:'🔗',label:'الرابط',sub:it.url,run:()=>editField(id,idx,'url')},
      {icon:'📁',label:'المجموعة',sub:it.group||'—',run:()=>editField(id,idx,'group')}
    ];
    if(isB)rows.push({icon:'🔢',label:'الرقم',sub:String(numOfRaw(it,idx)),run:()=>editField(id,idx,'num')});
    const fav=favs.has(it.url);
    rows.push({icon:fav?'★':'☆',label:fav?'إزالة من المفضلة':'إضافة إلى المفضلة',run:()=>{toggleFav(it.url);rerender()}});
    if(!isB){
      if(idx>0)rows.push({icon:'⬆',label:'نقل للأعلى',run:()=>move(id,idx,-1)});
      if(idx<its.length-1)rows.push({icon:'⬇',label:'نقل للأسفل',run:()=>move(id,idx,1)});
    }
    rows.push({icon:'🗑',label:'حذف القناة',danger:true,run:()=>push(k=>confirmChan(id,idx,k))});
    wantPv=true;
    show(it.name,rows,keep);
    pvPlay(it,true);
  }
  const setName=(it,v)=>{it.name=v;if(it.raw){const i=it.raw.lastIndexOf(',');it.raw=(i>-1?it.raw.slice(0,i):it.raw)+','+v}else it.raw='#EXTINF:-1,'+v};
  function setGroup(it,g){
    g=g.replace(/"/g,'');it.group=g;
    if(!it.raw)it.raw='#EXTINF:-1,'+it.name;
    if(/group-title="[^"]*"/.test(it.raw))it.raw=it.raw.replace(/group-title="[^"]*"/,'group-title="'+g+'"');
    else it.raw=it.raw.replace(/^#EXTINF:(-?\d+)/,'#EXTINF:$1 group-title="'+g+'"');
  }
  async function saveItems(id,its){
    const l=lists.find(x=>x.id===id);
    l.text=ser(its);l.n=its.length;l.upd=Date.now();
    await saveLists();
    if(active===id)openList(id);
  }
  async function editField(id,idx,field){
    const its=itemsOf(id),it=its[idx];if(!it)return;
    const ttl={name:'اسم القناة',url:'رابط القناة',group:'اسم المجموعة',num:'رقم القناة (من 1 إلى 9999)'}[field];
    const cur=field==='num'?String(numOfRaw(it,idx)):(it[field]||'');
    const v=await kbPrompt(ttl,cur,field==='url'?'en':(field==='num'?'sym':undefined));
    if(v===null)return;
    const val=v.trim();
    if(field==='num'){
      const n=parseInt(val,10);
      if(!(n>=1&&n<=9999)){toast('الرقم غير صالح');return}
      if(its.some((x,j)=>j!==idx&&numOfRaw(x,j)===n)){toast('هذا الرقم مستخدم لقناة أخرى');return}
      window.__chSet(window.__chKey(it),{n});toast('تم تغيير الرقم');back();return;
    }
    if(field==='url'&&!/^https?:\/\//i.test(val)){toast('الرابط يجب أن يبدأ بـ http أو https');return}
    if(!val&&field!=='group'){toast('لا يمكن ترك هذا الحقل فارغاً');return}
    if(id==='builtin'&&window.__chKey&&window.__chKey(it)!=null){
      window.__chSet(window.__chKey(it),{[field]:val});
    }else{
      if(field==='name')setName(it,val);else if(field==='group')setGroup(it,val);else it.url=val;
      await saveItems(id,its);
    }
    toast('تم الحفظ');rerender();
  }
  async function move(id,idx,d){
    const its=itemsOf(id),j=idx+d;
    if(j<0||j>=its.length)return;
    const t=its[idx];its[idx]=its[j];its[j]=t;
    await saveItems(id,its);
    setTop(k=>chan(id,j,k),focusIdx());
  }
  function confirmChan(id,idx){
    const its=itemsOf(id),it=its[idx];if(!it){back();return}
    show('حذف «'+it.name+'»؟',[
      {icon:'🗑',label:'نعم، احذف القناة',danger:true,run:async()=>{
        if(id==='builtin'&&window.__chKey&&window.__chKey(it)!=null){window.__chSet(window.__chKey(it),{deleted:true})}
        else{its.splice(idx,1);await saveItems(id,its)}
        toast('تم الحذف');stack.pop();back();
      }},
      {icon:'↩',label:'إلغاء',run:()=>back()}
    ],1);
  }

  /* ---------------- حول ---------------- */
  function about(keep){
    show('حول التطبيق',[
      {icon:'▶',label:'وصلة TV',sub:'الإصدار '+((A&&A.version)?A.version():APP_VERSION),run:()=>{}},
      {icon:'📍',label:'مجلد الملفات',sub:(A&&A.appDir)?A.appDir():'—',run:()=>{}},
      {sep:'لاسترجاع نسخة احتياطية: إضافة مصدر ← من ملف، أو البحث التلقائي.'}
    ],keep);
  }
})();
