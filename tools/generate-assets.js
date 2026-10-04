#!/usr/bin/env node
/**
 * tools/generate-assets.js — генератор графических ассетов приложения.
 *
 * Что делает:
 *   1. Копирует РЕАЛЬНЫЕ планы этажей     → assets/images/maps/plan_main_floor_*.svg
 *      из папки Map/ (Map/map1lower.svg, Map/map2lower.svg). Карта в приложении —
 *      именно эти файлы, поэтому они всегда должны совпадать с Map/.
 *   2. SVG-иконки типов POI               → assets/icons/poi_*.svg
 *   3. Логотип и splash-экран (PNG)       → assets/images/logo.png, splash.png
 *
 * Запуск:  npm run assets      (или: node tools/generate-assets.js)
 * Требуется пакет sharp (devDependency) для конвертации SVG → PNG.
 */
const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const DATA = path.join(ROOT, 'src', 'data');
/** Папка с исходными планами этажей (корень репозитория) */
const SOURCE_PLANS_DIR = path.join(ROOT, '..', 'Map');
const MAPS_DIR = path.join(ROOT, 'assets', 'images', 'maps');
const ICONS_DIR = path.join(ROOT, 'assets', 'icons');
const IMAGES_DIR = path.join(ROOT, 'assets', 'images');

// ---------------------------------------------------------------------------
// Загрузка данных
// ---------------------------------------------------------------------------

const floorsData = require(path.join(DATA, 'floors.json'));

const floors = floorsData.floors;

/** Фирменные цвета (совпадают с src/theme/light.js) */
const C = {
  background: '#0B0B14',
  primary: '#1A18E0',
};

// ---------------------------------------------------------------------------
// 1. Планы этажей (копия реальных SVG из папки Map/)
// ---------------------------------------------------------------------------

/**
 * Копирует план этажа из Map/<realPlan>.svg в assets/images/maps/<planFile>.
 * Имена берутся из src/data/floors.json — единого источника правды.
 */
function syncFloorPlans() {
  let copied = 0;
  for (const floor of floors) {
    if (!floor.realPlan || !floor.planFile) continue;
    const source = path.join(SOURCE_PLANS_DIR, `${floor.realPlan}.svg`);
    if (!fs.existsSync(source)) {
      console.error(`✖ План не найден: ${path.relative(ROOT, source)}`);
      process.exit(1);
    }
    const target = path.join(MAPS_DIR, floor.planFile);
    fs.copyFileSync(source, target);
    copied += 1;
    console.log(
      `✓ план ${floor.id}: ${path.relative(ROOT, source)} → ${path.relative(ROOT, target)}`
    );
  }
  if (!copied) {
    console.error('✖ В floors.json нет ни одного этажа с полями realPlan/planFile');
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// 2. SVG-иконки POI
// ---------------------------------------------------------------------------

const ICON_PATHS = {
  classroom:
    '<rect x="9" y="7" width="30" height="19" rx="2.5"/><path d="M17 30v9M31 30v9M13 39h22" stroke-linecap="round"/>',
  lab: '<path d="M20 6v11l-8 16a5 5 0 0 0 4.5 7h15a5 5 0 0 0 4.5-7l-8-16V6" /><path d="M17 6h14M16 30h16" />',
  office:
    '<rect x="7" y="15" width="34" height="23" rx="3.5"/><path d="M18 15v-4a2.5 2.5 0 0 1 2.5-2.5h7A2.5 2.5 0 0 1 30 11v4M7 26h34" />',
  library:
    '<path d="M10 9h10a4 4 0 0 1 4 4v26a4 4 0 0 0-4-4H10z"/><path d="M38 9H28a4 4 0 0 0-4 4v26a4 4 0 0 1 4-4h10z"/>',
  cafeteria: '<path d="M13 6v9a4 4 0 0 0 8 0V6"/><path d="M17 16v26"/><path d="M32 6c4.5 6 4.5 11 0 15v21"/>',
  toilet:
    '<circle cx="24" cy="11" r="5.5"/><path d="M24 18v13h-7l-2.5 14h19L31 31h-7"/>',
  wardrobe:
    '<path d="M24 7a4.5 4.5 0 0 1 4.5 4.5V14"/><path d="M10 27L24 15l14 12"/><path d="M10 27v14h28V27"/>',
  medical: '<path d="M20 7h8v13h13v8H28v13h-8V28H7v-8h13z"/>',
  hall: '<path d="M24 5l5.6 12.2 13.4 1.6-9.9 9.2 2.6 13.2L24 35.4 12.3 41.2l2.6-13.2-9.9-9.2 13.4-1.6z"/>',
  stairs: '<path d="M7 40h9v-9h9v-9h9v-9h9V8" stroke-linecap="round" stroke-linejoin="round"/>',
  elevator:
    '<rect x="11" y="5" width="26" height="38" rx="3"/><path d="M19 17l5-5 5 5M19 31l5 5 5-5"/>',
  entrance: '<rect x="13" y="5" width="22" height="38" rx="3"/><circle cx="29" cy="24" r="2.2"/>',
  parking:
    '<rect x="7" y="7" width="34" height="34" rx="8"/><path d="M20 35V15h8.5a6.5 6.5 0 0 1 0 13H20"/>',
  atm: '<rect x="5" y="11" width="38" height="26" rx="4"/><circle cx="24" cy="24" r="5.5"/><path d="M11 17h5" />',
  vending:
    '<path d="M11 15h23v15a8.5 8.5 0 0 1-8.5 8.5h-6A8.5 8.5 0 0 1 11 30z"/><path d="M34 19h4a4 4 0 0 1 0 8h-4M11 43h23"/>',
  transport:
    '<rect x="7" y="9" width="34" height="25" rx="4"/><path d="M7 23h34M14 38v4M34 38v4"/>',
  security: '<path d="M24 5l15 5.5v12.5c0 10.5-6.4 17-15 20-8.6-3-15-9.5-15-20V10.5z"/>',
};

const ICON_FILE_NAMES = {
  classroom: 'poi_classroom.svg',
  lab: 'poi_lab.svg',
  office: 'poi_office.svg',
  library: 'poi_library.svg',
  cafeteria: 'poi_cafeteria.svg',
  toilet: 'poi_toilet.svg',
  wardrobe: 'poi_wardrobe.svg',
  medical: 'poi_medical.svg',
  hall: 'poi_hall.svg',
  stairs: 'poi_stairs.svg',
  elevator: 'poi_elevator.svg',
  entrance: 'poi_entrance.svg',
  parking: 'poi_parking.svg',
  atm: 'poi_atm.svg',
  vending: 'poi_vending.svg',
  transport: 'poi_transport.svg',
  security: 'poi_security.svg',
};

function buildIconSvg(type) {
  return `<?xml version="1.0" encoding="UTF-8"?>
<!-- Иконка типа POI: ${type} (32x32 viewBox) -->
<svg xmlns="http://www.w3.org/2000/svg" width="48" height="48" viewBox="0 0 48 48">
  <g fill="none" stroke="${C.primary}" stroke-width="2.6" stroke-linejoin="round">
    ${ICON_PATHS[type]}
  </g>
</svg>
`;
}

// ---------------------------------------------------------------------------
// 3. Логотип и splash
// ---------------------------------------------------------------------------

/** SVG-логотип: переплетенные кольца (∞) + «ЛОГОС» + «ПУТЕВОДИТЕЛЬ» */
function buildLogoSvg({ width, height, withText = true }) {
  const cx = width / 2;
  const ringY = withText ? height * 0.34 : height / 2;
  const ringR = Math.min(width, height) * (withText ? 0.14 : 0.22);
  const gap = ringR * 0.62;
  const fontSize = Math.min(width, height) * (withText ? 0.13 : 0.2);
  const subFontSize = fontSize * 0.34;

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="${width}" height="${height}" viewBox="0 0 ${width} ${height}">
  <defs>
    <linearGradient id="grad" x1="0" y1="0" x2="1" y2="1">
      <stop offset="0%" stop-color="#5B57F0"/>
      <stop offset="100%" stop-color="#120EA8"/>
    </linearGradient>
  </defs>
  <rect width="${width}" height="${height}" fill="${C.background}"/>
  <g fill="none" stroke="url(#grad)" stroke-width="${ringR * 0.34}" stroke-linecap="round">
    <circle cx="${cx - gap}" cy="${ringY}" r="${ringR}"/>
    <circle cx="${cx + gap}" cy="${ringY}" r="${ringR}"/>
  </g>
  ${
    withText
      ? `<text x="${cx}" y="${ringY + ringR * 2.1}" fill="#FFFFFF" font-size="${fontSize}" font-weight="700"
             text-anchor="middle" letter-spacing="${fontSize * 0.06}" font-family="DejaVu Sans, sans-serif">ЛОГОС</text>
  <text x="${cx}" y="${ringY + ringR * 2.1 + subFontSize * 1.7}" fill="#8E92C9" font-size="${subFontSize}"
             font-weight="600" text-anchor="middle" letter-spacing="${subFontSize * 0.42}" font-family="DejaVu Sans, sans-serif">ПУТЕВОДИТЕЛЬ</text>`
      : ''
  }
</svg>
`;
}

// ---------------------------------------------------------------------------
// Запуск
// ---------------------------------------------------------------------------

function ensureDir(dir) {
  if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
}

async function main() {
  let sharp;
  try {
    sharp = require('sharp');
  } catch (error) {
    console.error('Не найден пакет sharp. Установите: npm install --save-dev sharp');
    process.exit(1);
  }

  ensureDir(MAPS_DIR);
  ensureDir(ICONS_DIR);
  ensureDir(IMAGES_DIR);

  // 1. Планы этажей — копия реальных SVG из папки Map/
  syncFloorPlans();

  // 2. Иконки POI
  for (const type of Object.keys(ICON_PATHS)) {
    const fileName = ICON_FILE_NAMES[type];
    const filePath = path.join(ICONS_DIR, fileName);
    fs.writeFileSync(filePath, buildIconSvg(type), 'utf8');
    console.log('✓ иконка:', path.relative(ROOT, filePath));
  }

  // 3. Логотип (1024×1024, тёмный фон — используется как иконка приложения)
  const logoSvg = buildLogoSvg({ width: 1024, height: 1024, withText: false });
  const logoPath = path.join(IMAGES_DIR, 'logo.png');
  await sharp(Buffer.from(logoSvg)).png().toFile(logoPath);
  console.log('✓ логотип:', path.relative(ROOT, logoPath));

  // 4. Splash (1242×2436)
  const splashSvg = buildLogoSvg({ width: 1242, height: 2436, withText: true });
  const splashPath = path.join(IMAGES_DIR, 'splash.png');
  await sharp(Buffer.from(splashSvg)).png().toFile(splashPath);
  console.log('✓ splash:', path.relative(ROOT, splashPath));

  console.log('\nГотово. Ассеты обновлены.');
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
