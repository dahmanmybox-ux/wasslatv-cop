/* ===== Xtream Codes: بث مباشر + أفلام + مسلسلات بفئاتها الحقيقية =====
   • المجموعات تأخذ أسماء فئات السيرفر («أفلام | أكشن»، «مسلسلات | دراما»…).
   • المسلسلات تُضاف كعناصر خفيفة، وحلقاتها تُحمَّل عند فتح المسلسل فقط (get_series_info). */
(function(){
  const clean=s=>String(s==null?'':s).replace(/"/g,"'").replace(/[\r\n]+/g,' ').trim();
  const nm=s=>clean(s).replace(/,/g,'،');
  async function api(host,u,p,action){
    const url=host+'/player_api.php?username='+encodeURIComponent(u)+'&password='+encodeURIComponent(p)+'&action='+action;
    try{const r=await fetch(url);if(!r.ok)return null;return await r.json()}catch(e){return null}
  }
  const catMap=arr=>{const m={};(Array.isArray(arr)?arr:[]).forEach(c=>{m[c.category_id]=clean(c.category_name)});return m};
  const arr=x=>Array.isArray(x)?x:[];

  async function build(host,u,p){
    host=host.trim().replace(/\/+$/,'');
    if(!/^https?:\/\//i.test(host))host='http://'+host;
    const acts=['get_live_categories','get_live_streams','get_vod_categories','get_vod_streams','get_series_categories','get_series'];
    const [lc,live,vc,vod,sc,ser]=await Promise.all(acts.map(a=>api(host,u,p,a)));
    const L=catMap(lc),V=catMap(vc),S=catMap(sc);
    const out=['#EXTM3U'];let n=0;
    arr(live).forEach(c=>{
      out.push('#EXTINF:-1 tvg-id="'+clean(c.epg_channel_id)+'" tvg-logo="'+clean(c.stream_icon)+'" group-title="'+(L[c.category_id]||'قنوات مباشرة')+'",'+nm(c.name));
      out.push(host+'/live/'+u+'/'+p+'/'+c.stream_id+'.m3u8');n++;
    });
    arr(vod).forEach(m=>{
      out.push('#EXTINF:-1 tvg-logo="'+clean(m.stream_icon)+'" group-title="أفلام | '+(V[m.category_id]||'عام')+'",'+nm(m.name));
      out.push(host+'/movie/'+u+'/'+p+'/'+m.stream_id+'.'+(m.container_extension||'mp4'));n++;
    });
    arr(ser).forEach(s=>{
      out.push('#EXTINF:-1 tvg-logo="'+clean(s.cover)+'" group-title="مسلسلات | '+(S[s.category_id]||'عام')+'",'+nm(s.name));
      out.push(host+'/player_api.php?username='+encodeURIComponent(u)+'&password='+encodeURIComponent(p)+'&action=get_series_info&series_id='+s.series_id);n++;
    });
    return{text:out.join('\n')+'\n',count:n,host};
  }

  /* إضافة سيرفر جديد: يعيد true عند النجاح */
  window.xtAdd=async function(host,u,p,name){
    const r=await build(host,u,p);
    if(!r.count)return false;
    const ok=await addList(r.text,name||'Xtream','');
    if(ok){
      const l=lists.find(x=>x.id===active);
      if(l){l.xt={host:r.host,u,p};await saveLists()}
    }
    return ok;
  };
  /* تحديث مصدر Xtream موجود */
  window.xtRefresh=async function(l){
    if(!l||!l.xt)return false;
    const r=await build(l.xt.host,l.xt.u,l.xt.p);
    if(!r.count)return false;
    const its=parseM3U(r.text);
    l.text=ser(its);l.n=its.length;l.upd=Date.now();
    await saveLists();
    if(active===l.id)openList(l.id);
    return true;
  };
})();
