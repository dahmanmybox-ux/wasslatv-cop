/* ===== لوحة مفاتيح داخل التطبيق (للريموت): عربي · English · أرقام ورموز ===== */
(function(){
  const dlg=$('kb'),view=$('kbv'),grid=$('kbg'),title=$('kbt');
  const LAY={
    ar:['ضصثقفغعهخحجد','شسيبلاتنمكط','ئءؤرىةوزظذ'],
    en:['qwertyuiop','asdfghjkl','zxcvbnm'],
    sym:['1234567890','/:.-_?&=@%','#+,;!()~*$']
  };
  let layout='ar',upper=false,val='',done=false,resolve=null;

  function key(label,cls,fn){
    const b=document.createElement('button');
    b.type='button';b.className='k'+(cls?' '+cls:'');b.textContent=label;b.onclick=fn;return b;
  }
  const ins=t=>{val+=t;paint()};
  function paint(){view.textContent=val||' '}
  function draw(){
    grid.textContent='';
    LAY[layout].forEach(r=>{
      const row=document.createElement('div');row.className='kbr';
      [...r].forEach(ch=>{
        const c=(layout==='en'&&upper)?ch.toUpperCase():ch;
        row.appendChild(key(c,'',()=>ins(c)));
      });
      grid.appendChild(row);
    });
    const quick=document.createElement('div');quick.className='kbr';
    ['http://','https://','www.','.com','.m3u'].forEach(q=>quick.appendChild(key(q,'w',()=>ins(q))));
    grid.appendChild(quick);
    const ctl=document.createElement('div');ctl.className='kbr';
    ctl.appendChild(key(layout==='ar'?'EN':layout==='en'?'123':'ع','w',()=>{layout=layout==='ar'?'en':layout==='en'?'sym':'ar';draw();focusFirst()}));
    if(layout==='en')ctl.appendChild(key(upper?'abc':'ABC','w',()=>{upper=!upper;draw();focusFirst()}));
    ctl.appendChild(key('مسافة','ww',()=>ins(' ')));
    ctl.appendChild(key('⌫','w',()=>{val=val.slice(0,-1);paint()}));
    ctl.appendChild(key('مسح','w',()=>{val='';paint()}));
    grid.appendChild(ctl);
    const end=document.createElement('div');end.className='kbr';
    end.appendChild(key('✔ تم','w ok',()=>{done=true;dlg.close()}));
    end.appendChild(key('✖ إلغاء','w',()=>{done=false;dlg.close()}));
    grid.appendChild(end);
  }
  function focusFirst(){const f=grid.querySelector('.k');if(f)f.focus({preventScroll:true})}

  dlg.addEventListener('close',()=>{
    const r=resolve;resolve=null;
    if(r)r(done?val:null);
  });

  /* kbPrompt('الاسم','قيمة ابتدائية') → نص أو null عند الإلغاء */
  window.kbPrompt=function(ttl,initial,startLayout){
    return new Promise(res=>{
      title.textContent=ttl||'';val=initial||'';done=false;resolve=res;
      layout=startLayout||(/^https?:|^www\./i.test(val)?'en':'ar');upper=false;
      draw();paint();dlg.showModal();focusFirst();
    });
  };
  /* ربط خانة كتابة عادية (النوافذ القديمة) باللوحة */
  window.kbEdit=async function(el){
    const r=await window.kbPrompt(el.getAttribute('placeholder')||el.getAttribute('aria-label')||'تحرير',el.value,
      (el.type==='url'||/^https?:/i.test(el.value))?'en':undefined);
    if(r!==null){el.value=r;el.dispatchEvent(new Event('input',{bubbles:true}));el.dispatchEvent(new Event('change',{bubbles:true}))}
  };
})();
