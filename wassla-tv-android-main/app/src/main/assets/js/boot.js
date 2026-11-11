(function(){var k='m3u_bgphoto',h=document.documentElement,c=document.getElementById('bgphoto'),v=null;
try{v=localStorage.getItem(k)}catch(e){}
var on=v!=='0';h.classList.toggle('nophoto',!on);c.checked=on;
c.onchange=function(){h.classList.toggle('nophoto',!c.checked);try{localStorage.setItem(k,c.checked?'1':'0')}catch(e){}};})();
