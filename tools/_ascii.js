#!/usr/bin/env node
/* eslint-disable */
// ASCII-дамп региона плана: node tools/_ascii.js <svg> <x0> <y0> <x1> <y1> [шаг]
// Условные обозначения: '.' белый/фон, 'B' комната #DFE6FF, '#' стена #ACAEB3,
// 'G' серая комната #D9D9D9, 'D' тёмная комната #878DA2, '+' синий элемент.
const sharp = require('sharp');
const fs = require('fs');
const path = require('path');

const [svg, x0s, y0s, x1s, y1s, stepS] = process.argv.slice(2);
const x0 = Number(x0s), y0 = Number(y0s), x1 = Number(x1s), y1 = Number(y1s);
const step = Number(stepS || 2);

const text = fs.readFileSync(path.resolve(svg), 'utf8');
const vb = /viewBox="([\d.\s-]+)"/.exec(text);
const [vbx, vby, vbw, vbh] = vb ? vb[1].trim().split(/[\s,]+/).map(Number) : [0, 0, 1000, 1000];

const RENDER_W = 4000;
const scale = RENDER_W / vbw;
const img = sharp(path.resolve(svg), { density: 96 * 8 }).resize(RENDER_W, null);

const left = Math.max(0, Math.round((x0 - vbx) * scale));
const top = Math.max(0, Math.round((y0 - vby) * scale));
const width = Math.round((x1 - x0) * scale);
const height = Math.round((y1 - y0) * scale);

img.extract({ left, top, width, height }).raw().toBuffer({ resolveWithObject: true })
  .then(({ data, info }) => {
    const cw = info.width, ch = info.height, channels = info.channels;
    const w = Math.ceil((x1 - x0) / step), h = Math.ceil((y1 - y0) / step);
    const lines = [];
    for (let j = 0; j < h; j++) {
      let line = '';
      for (let i = 0; i < w; i++) {
        const px = Math.min(cw - 1, Math.round(i * step * scale));
        const py = Math.min(ch - 1, Math.round(j * step * scale));
        const idx = (py * cw + px) * channels;
        const r = data[idx], g = data[idx + 1], b = data[idx + 2];
        let c = '?';
        if (r > 240 && g > 240 && b > 240) c = '.';
        else if (Math.abs(r - 223) < 15 && Math.abs(g - 230) < 15 && Math.abs(b - 255) < 15) c = 'B';
        else if (r < 60 && g < 60 && b > 180) c = '+';
        else if (Math.abs(r - 172) < 25 && Math.abs(g - 174) < 25 && Math.abs(b - 179) < 25) c = '#';
        else if (Math.abs(r - 217) < 12 && Math.abs(g - 217) < 12 && Math.abs(b - 217) < 12) c = 'G';
        else if (Math.abs(r - 135) < 30 && Math.abs(g - 141) < 30 && Math.abs(b - 162) < 30) c = 'D';
        else if (r > 200 && g > 200 && b > 200) c = '-';
        else c = '?';
        line += c;
      }
      lines.push(line);
    }
    console.log(`x=${x0}..${x1}  y=${y0}..${y1}  шаг ${step}`);
    lines.forEach((l, j) => console.log(String(Math.round(y0 + j * step)).padStart(5) + ' ' + l));
  })
  .catch((e) => { console.error(e.message); process.exit(1); });
