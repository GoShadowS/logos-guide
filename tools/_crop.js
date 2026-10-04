const sharp=require('sharp');
const [,,src,out,x0,y0,x1,y1,zoom]=process.argv;
const svg=require('fs').readFileSync(src,'utf8');
const vb=(svg.match(/viewBox="([^"]+)"/)||[])[1].split(/\s+/).map(Number);
const w=+x1-(+x0), h=+y1-(+y0);
const inner=svg.replace(/<svg[^>]*>/, '').replace(/<\/svg>\s*$/,'');
const out_svg=`<svg xmlns="http://www.w3.org/2000/svg" width="${w*zoom}" height="${h*zoom}" viewBox="${x0} ${y0} ${w} ${h}"><rect x="${x0}" y="${y0}" width="${w}" height="${h}" fill="white"/>${inner}</svg>`;
require('fs').writeFileSync('/tmp/_crop.svg', out_svg);
sharp('/tmp/_crop.svg').png().toFile(out).then(()=>console.log('ok',out));
