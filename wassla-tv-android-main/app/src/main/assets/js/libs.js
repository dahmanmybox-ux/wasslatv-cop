// تحميل مكتبات التشغيل: النسخة المحلية أولاً (js/lib/) ثم الإنترنت (CDN) كاحتياط
(function(){
  var L=[['js/lib/hls.min.js','https://cdn.jsdelivr.net/npm/hls.js@1.5.17/dist/hls.min.js'],
         ['js/lib/mpegts.min.js','https://cdn.jsdelivr.net/npm/mpegts.js@1.7.3/dist/mpegts.min.js']];
  L.forEach(function(p){
    var s=document.createElement('script');s.async=true;s.src=p[0];
    s.onerror=function(){var f=document.createElement('script');f.async=true;f.src=p[1];document.head.appendChild(f)};
    document.head.appendChild(s);
  });
})();
