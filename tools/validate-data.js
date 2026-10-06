#!/usr/bin/env node
/**
 * tools/validate-data.js
 *
 * Проверка целостности данных и ресурсов приложения «ЛОГОС: Путеводитель».
 * Запуск: node tools/validate-data.js  (или npm run validate)
 *
 * Что проверяется:
 *   1. Синтаксис всех JSON-файлов в src/data/;
 *   2. Уникальность идентификаторов (аудитории, POI, этажи, корпуса);
 *   3. Ссылочная целостность: floorId и buildingId;
 *   4. Планы этажей: встроенные SVG-файлы существуют, viewBox совпадает с floors.json;
 *   5. Двери аудиторий — на границе помещения и «смотрят» в проходимую зону;
 *   6. Лестницы/лифты — точки a и b обе проходимы на своих этажах;
 *   7. Локализация: ru/en совпадают, все используемые в коде ключи существуют,
 *      в файлах нет «мёртвых» ключей;
 *   8. Иконки: все имена глифов существуют в наборе MaterialCommunityIcons;
 *   9. Регрессии: нет данных расписания, QR-сканера, карты улицы (campus) и эмодзи; экран расписания может быть только пустой заглушкой.
 *
 * Код завершения: 0 — всё хорошо, 1 — найдены ошибки.
 */

'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.join(__dirname, '..');
const DATA_DIR = path.join(ROOT, 'src', 'data');
const LOC_DIR = path.join(ROOT, 'src', 'localization');
const SRC_DIR = path.join(ROOT, 'src');
const MAPS_DIR = path.join(ROOT, 'assets', 'images', 'maps');
const SOURCE_PLANS_DIR = path.join(ROOT, '..', 'Map');
const GLYPHMAP_PATH = path.join(
  ROOT,
  'node_modules',
  '@expo',
  'vector-icons',
  'build',
  'vendor',
  'react-native-vector-icons',
  'glyphmaps',
  'MaterialCommunityIcons.json'
);

// --- Константы, синхронизированные с src/services/graph.js -------------------
const WALKABLE_AREA_KINDS = new Set(['corridor', 'path', 'parking', 'plaza']);
const WALKABLE_ROOM_TYPES = new Set(['stairs', 'elevator', 'entrance']);
const KNOWN_POI_TYPES = new Set([
  'classroom', 'lab', 'office', 'library', 'cafeteria', 'toilet', 'wardrobe',
  'medical', 'hall', 'stairs', 'elevator', 'entrance', 'parking', 'atm',
  'vending', 'transport', 'security',
]);
/** Этажей территории/улицы в приложении быть не должно */
const FORBIDDEN_FLOOR_IDS = new Set(['campus', 'street', 'outside']);

const errors = [];
const warnings = [];
const notes = [];

function fail(msg) { errors.push(msg); }
function warn(msg) { warnings.push(msg); }
function note(msg) { notes.push(msg); }

// --- Загрузка JSON -----------------------------------------------------------
function loadJson(name) {
  const file = path.join(DATA_DIR, `${name}.json`);
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch (e) {
    fail(`${name}.json — не читается: ${e.message}`);
    return null;
  }
}

function loadLocalization() {
  const out = {};
  for (const lang of ['ru', 'en']) {
    try {
      out[lang] = JSON.parse(fs.readFileSync(path.join(LOC_DIR, `${lang}.json`), 'utf8'));
    } catch (e) {
      fail(`localization/${lang}.json — не читается: ${e.message}`);
    }
  }
  return out;
}

const floors = loadJson('floors');
const rooms = loadJson('rooms');
const pois = loadJson('poi');
const buildings = loadJson('buildings');
const graph = loadJson('graph');
const config = loadJson('config');
const gpsCalibration = loadJson('gps-calibration');

const roomsList = rooms && rooms.rooms ? rooms.rooms : [];
const floorList = floors && floors.floors ? floors.floors : [];
const poiList = pois && pois.pois ? pois.pois : [];
const buildingList = buildings && buildings.buildings ? buildings.buildings : [];

const floorById = new Map(floorList.map((f) => [f.id, f]));

// --- Вспомогательные геометрические функции ---------------------------------
function pointInRect(x, y, r) {
  return x >= r.x && x <= r.x + r.w && y >= r.y && y <= r.y + r.h;
}

function isWalkablePoint(floor, x, y) {
  for (const area of floor.areas || []) {
    if (WALKABLE_AREA_KINDS.has(area.kind) && pointInRect(x, y, area)) return true;
  }
  for (const room of roomsList) {
    if (room.floorId !== floor.id) continue;
    if (WALKABLE_ROOM_TYPES.has(room.type) && pointInRect(x, y, room)) return true;
  }
  return false;
}

/** Лежит ли точка на границе прямоугольника (с допуском) */
function onRectBorder(x, y, r, eps = 1) {
  const nearX = Math.abs(x - r.x) <= eps || Math.abs(x - (r.x + r.w)) <= eps;
  const nearY = Math.abs(y - r.y) <= eps || Math.abs(y - (r.y + r.h)) <= eps;
  const insideX = x >= r.x - eps && x <= r.x + r.w + eps;
  const insideY = y >= r.y - eps && y <= r.y + r.h + eps;
  return (nearX && insideY) || (nearY && insideX);
}

/** «Смотрит» ли точка на проходимую зону (ищем в радиусе) */
function looksAtWalkable(floor, x, y, radius = 30) {
  for (let dx = -radius; dx <= radius; dx += 5) {
    for (let dy = -radius; dy <= radius; dy += 5) {
      if (Math.hypot(dx, dy) > radius) continue;
      if (isWalkablePoint(floor, x + dx, y + dy)) return true;
    }
  }
  return false;
}

// --- 1. Уникальность идентификаторов ----------------------------------------
function checkUnique(list, label, idField = 'id') {
  const seen = new Map();
  for (const item of list) {
    const id = item[idField];
    if (!id) { fail(`${label}: элемент без поля ${idField}`); continue; }
    if (seen.has(id)) fail(`${label}: дубликат идентификатора «${id}»`);
    seen.set(id, item);
  }
  note(`${label}: проверено ${seen.size} элементов`);
}

if (floors) checkUnique(floorList, 'floors.json');
if (rooms) checkUnique(roomsList, 'rooms.json');
if (pois) checkUnique(poiList, 'poi.json');
if (buildings) checkUnique(buildingList, 'buildings.json');

// --- 2. Этажи и файлы планов ------------------------------------------------
if (floors) {
  for (const floor of floorList) {
    if (!floor.id || typeof floor.level !== 'number') {
      fail(`floors.json: этаж без id или level (${JSON.stringify(floor).slice(0, 80)})`);
      continue;
    }
    if (FORBIDDEN_FLOOR_IDS.has(floor.id)) {
      fail(`floors.json: этаж «${floor.id}» — в приложении только внутренние планы колледжа`);
    }
    if (!floor.width || !floor.height) {
      fail(`floors.json: этаж «${floor.id}» без width/height`);
    }
    if (!floor.unitsPerMeter || floor.unitsPerMeter <= 0) {
      fail(`floors.json: этаж «${floor.id}» без unitsPerMeter — расстояния в метрах считаются неверно`);
    }

    // Проверяем встроенную копию плана. Если исходная папка Map/ доступна,
    // используем её; в чистом checkout источником служит SVG из assets/.
    if (!floor.realPlan || !floor.planFile) {
      fail(`floors.json: этаж «${floor.id}» без realPlan/planFile`);
    } else {
      const source = path.join(SOURCE_PLANS_DIR, `${floor.realPlan}.svg`);
      const target = path.join(MAPS_DIR, floor.planFile);
      if (!fs.existsSync(target)) {
        fail(`floors.json: план не найден в assets/images/maps/${floor.planFile}`);
      }
      const planPath = fs.existsSync(source) ? source : target;
      if (!fs.existsSync(source) && fs.existsSync(target)) {
        note(`план ${floor.id}: исходник Map/ не включён в checkout, проверяется копия assets/`);
      }
      if (fs.existsSync(planPath)) {
        const svg = fs.readFileSync(planPath, 'utf8');
        const viewBox = /viewBox="([\d.\s-]+)"/.exec(svg);
        if (!viewBox) {
          fail(`${path.relative(ROOT, planPath)}: нет атрибута viewBox`);
        } else {
          const [, , w, h] = viewBox[1].trim().split(/[\s,]+/).map(Number);
          if (w !== floor.width || h !== floor.height) {
            fail(`floors.json: этаж «${floor.id}» ${floor.width}×${floor.height}, `
              + `а viewBox плана ${w}×${h} — координаты помещений «поедут»`);
          }
        }
      }
    }

    const areaIds = new Set();
    for (const area of floor.areas || []) {
      if (!area.id) fail(`floors.json: этаж «${floor.id}» — область без id`);
      if (areaIds.has(area.id)) fail(`floors.json: этаж «${floor.id}» — дубль области «${area.id}»`);
      areaIds.add(area.id);
      if (typeof area.x !== 'number' || typeof area.y !== 'number' ||
          typeof area.w !== 'number' || typeof area.h !== 'number') {
        fail(`floors.json: область «${area.id}» этажа «${floor.id}» — неполные координаты`);
      }
    }
    if (!areaIds.size) warn(`floors.json: этаж «${floor.id}» не содержит ни одной области`);
  }
}

// --- 2.5. GPS-геопривязка планов -------------------------------------------
if (gpsCalibration) {
  const calibrationFloors = gpsCalibration.floors || {};
  for (const floor of floorList) {
    const points = calibrationFloors[floor.id]?.controlPoints || [];
    if (points.length < 3) {
      fail(`gps-calibration.json: для этажа «${floor.id}» нужны минимум 3 опорные точки`);
      continue;
    }
    const labels = new Set();
    for (const point of points) {
      const tag = `gps-calibration.json [${floor.id}/${point.label || '?'}]`;
      if (labels.has(point.label)) fail(`${tag}: повторяется метка опорной точки`);
      labels.add(point.label);
      if (!Number.isFinite(point.latitude) || point.latitude < -90 || point.latitude > 90 ||
          !Number.isFinite(point.longitude) || point.longitude < -180 || point.longitude > 180) {
        fail(`${tag}: некорректные координаты WGS84`);
      }
      if (!Number.isFinite(point.x) || !Number.isFinite(point.y) ||
          point.x < 0 || point.x > floor.width || point.y < 0 || point.y > floor.height) {
        fail(`${tag}: X/Y должны попадать в viewBox плана этажа`);
      }
    }
    let nonCollinear = false;
    for (let i = 0; i < points.length && !nonCollinear; i += 1) {
      for (let j = i + 1; j < points.length && !nonCollinear; j += 1) {
        for (let k = j + 1; k < points.length; k += 1) {
          const area2 = (points[j].x - points[i].x) * (points[k].y - points[i].y)
            - (points[j].y - points[i].y) * (points[k].x - points[i].x);
          if (Math.abs(area2) > 1) { nonCollinear = true; break; }
        }
      }
    }
    if (!nonCollinear) fail(`gps-calibration.json: точки этажа «${floor.id}» должны быть не на одной линии`);
    note(`GPS ${floor.id}: опорных точек — ${points.length}`);
  }
  for (const floorId of Object.keys(calibrationFloors)) {
    if (!floorById.has(floorId)) fail(`gps-calibration.json: неизвестный этаж «${floorId}»`);
  }
}

// --- 3. Аудитории -----------------------------------------------------------
if (rooms) {
  for (const room of roomsList) {
    const tag = `rooms.json [${room.id || '?'}]`;

    if (!room.floorId || !floorById.has(room.floorId)) {
      fail(`${tag}: неизвестный floorId «${room.floorId}»`);
      continue;
    }
    if (Object.prototype.hasOwnProperty.call(room, 'teacherId')) {
      fail(`${tag}: teacherId запрещён — каталог преподавателей удалён`);
    }
    if (!room.type) { fail(`${tag}: не указан type`); continue; }
    if (!KNOWN_POI_TYPES.has(room.type)) {
      fail(`${tag}: неизвестный тип «${room.type}» (ожидался один из: ${[...KNOWN_POI_TYPES].join(', ')})`);
    }
    if (typeof room.x !== 'number' || typeof room.y !== 'number' ||
        typeof room.w !== 'number' || typeof room.h !== 'number') {
      fail(`${tag}: неполные координаты (нужны x, y, w, h)`);
      continue;
    }
    if (room.w <= 0 || room.h <= 0) fail(`${tag}: нулевые или отрицательные размеры`);

    if (!room.name || !room.name.ru || !room.name.en) {
      fail(`${tag}: нет названия (name.ru / name.en)`);
    } else if (room.name.ru.length > 60) {
      warn(`${tag}: слишком длинное название «${room.name.ru}»`);
    }
    if (!room.description || !room.description.ru || !room.description.en) {
      warn(`${tag}: нет описания (description.ru / description.en)`);
    }

    if (!room.door || typeof room.door.x !== 'number' || typeof room.door.y !== 'number') {
      fail(`${tag}: не указана дверь (door.x / door.y)`);
      continue;
    }
    const floor = floorById.get(room.floorId);
    if (!onRectBorder(room.door.x, room.door.y, room)) {
      fail(`${tag}: дверь (${room.door.x}, ${room.door.y}) лежит НЕ на границе помещения `
        + `(x=${room.x}..${room.x + room.w}, y=${room.y}..${room.y + room.h})`);
    }
    if (room.type !== 'entrance' && !looksAtWalkable(floor, room.door.x, room.door.y)) {
      fail(`${tag}: дверь (${room.door.x}, ${room.door.y}) не примыкает к проходимой зоне `
        + `(коридор/дорожка) — маршрут может не построиться`);
    }

    if (!Array.isArray(room.aliases) || room.aliases.length === 0) {
      warn(`${tag}: нет aliases — аудитория будет находиться только по номеру`);
    }
  }
}

// --- 4. POI -----------------------------------------------------------------
if (pois) {
  for (const poi of poiList) {
    const tag = `poi.json [${poi.id || '?'}]`;
    if (!poi.floorId || !floorById.has(poi.floorId)) {
      fail(`${tag}: неизвестный floorId «${poi.floorId}»`);
      continue;
    }
    if (FORBIDDEN_FLOOR_IDS.has(poi.floorId)) {
      fail(`${tag}: точка на этаже «${poi.floorId}» — карта улицы удалена`);
    }
    if (!poi.type || !KNOWN_POI_TYPES.has(poi.type)) {
      fail(`${tag}: неизвестный тип «${poi.type}»`);
    }
    if (typeof poi.x !== 'number' || typeof poi.y !== 'number') {
      fail(`${tag}: нет координат (x, y)`);
    }
    if (!poi.name || !poi.name.ru || !poi.name.en) {
      fail(`${tag}: нет названия (name.ru / name.en)`);
    }
  }
}

// --- 5. Корпуса -------------------------------------------------------------
if (buildings) {
  for (const b of buildingList) {
    const tag = `buildings.json [${b.id || '?'}]`;
    if (!b.floors || !b.floors.length) fail(`${tag}: не указаны floors`);
    for (const fId of b.floors || []) {
      if (!floorById.has(fId)) fail(`${tag}: неизвестный этаж «${fId}»`);
    }
    for (const key of ['campusAreaId', 'campusAreaIds']) {
      if (b[key]) fail(`${tag}: поле «${key}» относится к удалённой карте территории`);
    }
  }
}

// --- 7. Граф навигации ------------------------------------------------------
if (graph) {
  for (const link of graph.verticalLinks || []) {
    const tag = `graph.json verticalLinks (${link.kind})`;
    if (!['stairs', 'elevator'].includes(link.kind)) fail(`${tag}: неизвестный kind «${link.kind}»`);
    for (const side of ['a', 'b']) {
      const p = link[side];
      if (!p) { fail(`${tag}: отсутствует точка «${side}»`); continue; }
      if (!p.floorId || !floorById.has(p.floorId)) { fail(`${tag}: точка ${side} — неизвестный floorId «${p.floorId}»`); continue; }
      const floor = floorById.get(p.floorId);
      if (!isWalkablePoint(floor, p.x, p.y)) {
        fail(`${tag}: точка ${side} (${p.x}, ${p.y}) на этаже «${p.floorId}» не проходима `
          + `— лестница должна пересекать коридор или лестничную клетку`);
      }
    }
  }
  if (graph.entranceLinks && graph.entranceLinks.length) {
    fail('graph.json: entranceLinks описывают переход с территории — карта улицы удалена');
  }
  note(`graph.json: межэтажных связей — ${(graph.verticalLinks || []).length}`);
}

// --- 8. Конфигурация --------------------------------------------------------
if (config) {
  if (config.campus) fail('config.json: блок campus относится к удалённой карте территории');
  const allowed = {
    language: ['system', 'ru', 'en'],
    theme: ['system', 'light', 'dark'],
  };
  for (const [key, values] of Object.entries(allowed)) {
    const value = config.defaults && config.defaults[key];
    if (value && !values.includes(value)) {
      fail(`config.json: defaults.${key} = «${value}» — недопустимое значение`);
    }
  }
  const startFloor = config.defaults && config.defaults.startFloorId;
  if (!startFloor || !floorById.has(startFloor)) {
    fail(`config.json: defaults.startFloorId «${startFloor}» не найден в floors.json`);
  }
}

// --- 8.5. Конфигурация приложения (app.json / package.json) ------------------
const readJsonSafe = (rel) => {
  const abs = path.join(ROOT, rel);
  if (!fs.existsSync(abs)) return null;
  try {
    return JSON.parse(fs.readFileSync(abs, 'utf8'));
  } catch (error) {
    fail(`${rel}: не удалось разобрать JSON (${error.message})`);
    return null;
  }
};

const appJson = readJsonSafe('app.json');
const packageJson = readJsonSafe('package.json');

if (appJson) {
  const expo = appJson.expo || {};
  const extra = expo.extra || {};
  if (extra.campusCenter) {
    fail('app.json: extra.campusCenter относится к удалённой карте территории');
  }
  const raw = JSON.stringify(appJson).toLowerCase();
  if (raw.includes('yandex')) fail('app.json: найдены упоминания Яндекс.Карт — внешняя карта не используется');
  const androidPermissions = (expo.android && expo.android.permissions) || [];
  if (!androidPermissions.includes('android.permission.RECORD_AUDIO')) {
    fail('app.json: нет android.permission.RECORD_AUDIO — голосовой поиск не заработает на Android');
  }
  for (const permission of ['android.permission.ACCESS_COARSE_LOCATION', 'android.permission.ACCESS_FINE_LOCATION']) {
    if (!androidPermissions.includes(permission)) {
      fail(`app.json: нет ${permission} — GPS-позицию нельзя будет получить на Android`);
    }
  }
  if (!expo.plugins || !expo.plugins.some((p) => (Array.isArray(p) ? p[0] : p) === 'expo-location')) {
    fail('app.json: не подключён плагин expo-location');
  }
  if (!expo.plugins || !expo.plugins.some((p) => (Array.isArray(p) ? p[0] : p) === 'expo-speech-recognition')) {
    fail('app.json: не подключён плагин expo-speech-recognition (голосовой поиск)');
  }
  note(`app.json: версия ${expo.version || '?'}, разрешений Android — ${androidPermissions.length}`);
}

if (packageJson) {
  const deps = { ...(packageJson.dependencies || {}), ...(packageJson.devDependencies || {}) };
  for (const banned of ['expo-network', 'react-native-webview']) {
    if (deps[banned]) fail(`package.json: зависимость ${banned} осталась — она нужна была карте улицы`);
  }
  for (const required of ['expo-speech-recognition', 'expo-location', 'react-native-svg', 'react-native-reanimated']) {
    if (!deps[required]) fail(`package.json: нет зависимости ${required}`);
  }
  for (const script of ['validate', 'test', 'smoke', 'assets']) {
    if (!(packageJson.scripts || {})[script]) fail(`package.json: нет скрипта «${script}»`);
  }
}

// --- 9. Локализация ---------------------------------------------------------

/** Плоский список ключей: { 'a.b.c': value } */
function flatten(obj, prefix = '') {
  return Object.keys(obj || {}).reduce((acc, k) => {
    const key = prefix ? `${prefix}.${k}` : k;
    if (obj[k] && typeof obj[k] === 'object' && !Array.isArray(obj[k])) {
      Object.assign(acc, flatten(obj[k], key));
    } else {
      acc[key] = obj[k];
    }
    return acc;
  }, {});
}

/** Все .js-файлы приложения */
function listSourceFiles(dir) {
  const out = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) out.push(...listSourceFiles(full));
    else if (entry.name.endsWith('.js')) out.push(full);
  }
  return out;
}

const sourceFiles = listSourceFiles(SRC_DIR);
const sourceTexts = sourceFiles.map((file) => ({
  file: path.relative(ROOT, file),
  text: fs.readFileSync(file, 'utf8'),
}));

const loc = loadLocalization();
if (loc.ru && loc.en) {
  const ruFlat = flatten(loc.ru);
  const enFlat = flatten(loc.en);
  const ruKeys = Object.keys(ruFlat);
  const enKeys = Object.keys(enFlat);

  const missingInEn = ruKeys.filter((k) => !(k in enFlat));
  const missingInRu = enKeys.filter((k) => !(k in ruFlat));
  for (const k of missingInEn) fail(`localization: ключ «${k}» есть в ru.json, но отсутствует в en.json`);
  for (const k of missingInRu) fail(`localization: ключ «${k}» есть в en.json, но отсутствует в ru.json`);
  for (const k of ruKeys) {
    if (!String(ruFlat[k] ?? '').trim()) warn(`localization: пустое значение ключа «${k}» в ru.json`);
  }

  // --- Ключи, используемые в коде ------------------------------------------
  const used = new Set();
  const pluralBases = new Set();
  for (const { text } of sourceTexts) {
    for (const m of text.matchAll(/\btp\(\s*'([a-zA-Z0-9_.]+)'/g)) pluralBases.add(m[1]);
    for (const m of text.matchAll(/(?<![a-zA-Z])t\(\s*'([a-zA-Z0-9_.]+)'/g)) {
      // Префиксы динамических ключей (t('filter.' + id)) ключами не считаются
      if (!m[1].endsWith('.')) used.add(m[1]);
    }
  }
  // Для tp('search.found', n) нужны формы foundOne / foundFew / foundMany
  for (const base of pluralBases) {
    for (const suffix of ['One', 'Few', 'Many']) used.add(`${base}${suffix}`);
  }

  // Динамические ключи: t('poiType.' + type), t('quick.' + id), t('filter.' + id)
  const poiTypesText = fs.readFileSync(path.join(SRC_DIR, 'theme', 'poiTypes.js'), 'utf8');
  const dynamicGroups = {
    'poiType.': [...poiTypesText.matchAll(/^\s{2}([a-z]+):\s*\{\s*icon:/gm)].map((m) => m[1]),
    'quick.': blockIds(poiTypesText, 'QUICK_CATEGORIES'),
    'filter.': blockIds(poiTypesText, 'MAP_FILTERS'),
  };
  for (const [prefix, ids] of Object.entries(dynamicGroups)) {
    for (const id of ids) used.add(prefix + id);
  }

  const missing = [...used].filter((key) => !(key in ruFlat)).sort();
  for (const key of missing) fail(`localization: ключ «${key}» используется в коде, но отсутствует в ru.json/en.json`);

  const unused = ruKeys.filter((key) => !used.has(key)).sort();
  for (const key of unused) warn(`localization: ключ «${key}» не используется в коде`);

  if (!missingInEn.length && !missingInRu.length && !missing.length) {
    note(`localization: ru и en совпадают (${ruKeys.length} ключей), все ключи кода найдены`);
  }
}

/** Идентификаторы внутри массива констант (id: '...') */
function blockIds(text, name) {
  const match = new RegExp(`${name}\\s*=\\s*\\[([\\s\\S]*?)\\n\\];`).exec(text);
  if (!match) return [];
  return [...match[1].matchAll(/id:\s*'([^']+)'/g)].map((m) => m[1]);
}

// --- 10. Иконки (MaterialCommunityIcons) ------------------------------------
let glyphNames = null;
if (fs.existsSync(GLYPHMAP_PATH)) {
  glyphNames = new Set(Object.keys(JSON.parse(fs.readFileSync(GLYPHMAP_PATH, 'utf8'))));
}

if (glyphNames) {
  const usedIcons = new Set();
  const collect = (name) => {
    if (typeof name === 'string' && /^[a-z0-9-]+$/.test(name)) usedIcons.add(name);
  };
  for (const { text } of sourceTexts) {
    for (const m of text.matchAll(/\bname="([a-z0-9-]+)"/g)) collect(m[1]);
    for (const m of text.matchAll(/\bicon="([a-z0-9-]+)"/g)) collect(m[1]);
    for (const m of text.matchAll(/\bicon:\s*'([a-z0-9-]+)'/g)) collect(m[1]);
    for (const m of text.matchAll(/\bicon=\{([^}]*)\}/g)) {
      for (const inner of m[1].matchAll(/'([a-z0-9-]+)'/g)) collect(inner[1]);
    }
  }
  const unknown = [...usedIcons].filter((name) => !glyphNames.has(name)).sort();
  for (const name of unknown) fail(`иконка «${name}» отсутствует в наборе MaterialCommunityIcons`);
  note(`иконки: проверено ${usedIcons.size} имён глифов (MaterialCommunityIcons)`);
} else {
  warn('иконки: набор MaterialCommunityIcons не найден (npm install) — проверка пропущена');
}

// Каждый тип точки из данных должен иметь перевод poiType.<type>
if (loc.ru) {
  const poiTypeKeys = new Set(Object.keys(loc.ru.poiType || {}));
  const dataTypes = new Set([
    ...roomsList.map((r) => r.type),
    ...poiList.map((p) => p.type),
  ]);
  for (const type of dataTypes) {
    if (!poiTypeKeys.has(type)) fail(`localization: нет перевода poiType.${type} для типа из данных`);
  }
}

// --- 11. Регрессии: удалённые разделы и эмодзи ------------------------------
const forbiddenFiles = [
  ['src/screens/QrScanScreen.js', 'сканирование QR-кодов удалено'],
  ['src/data/schedule.json', 'данные расписания не добавлялись'],
  ['src/data/teachers.json', 'каталог преподавателей удалён'],
  ['src/components/YandexMapView.js', 'онлайн-карта улицы удалена'],
  ['src/services/yandexMap.js', 'онлайн-карта улицы удалена'],
  ['src/components/FloorPlan.js', 'синтетические планы заменены реальными SVG'],
];
for (const [rel, reason] of forbiddenFiles) {
  if (fs.existsSync(path.join(ROOT, rel))) fail(`${rel}: файл вернулся в проект (${reason})`);
}

// Эмодзи в интерфейсе недопустимы — используются векторные иконки.
// Проверяются и код (src/**\/*.js), и тексты переводов (localization/**\/*.json).
// Типографские стрелки «→» и знаки вроде «−» в комментариях эмодзи не считаются.
const emojiRe = /[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{FE0F}]/u;
const emojiHits = [];
const scanForEmoji = (relPath, text) => {
  text.split('\n').forEach((line, index) => {
    const match = emojiRe.exec(line);
    if (match) emojiHits.push(`${relPath}:${index + 1} «${match[0]}»`);
  });
};
for (const { file, text } of sourceTexts) scanForEmoji(file, text);
for (const lang of ['ru', 'en']) {
  const file = path.join(LOC_DIR, `${lang}.json`);
  if (fs.existsSync(file)) {
    scanForEmoji(path.relative(ROOT, file), fs.readFileSync(file, 'utf8'));
  }
}
for (const hit of emojiHits) fail(`эмодзи в приложении: ${hit} — замените на векторную иконку`);
if (!emojiHits.length) note('эмодзи в src/ и переводах не найдены — используются векторные иконки');

// --- Отчёт ------------------------------------------------------------------
console.log('');
console.log('═══════════════════════════════════════════════════════════════');
console.log('  ЛОГОС: Путеводитель — проверка данных');
console.log('═══════════════════════════════════════════════════════════════');

if (warnings.length) {
  console.log(`\n!  Предупреждений: ${warnings.length}`);
  warnings.slice(0, 40).forEach((w) => console.log(`   • ${w}`));
  if (warnings.length > 40) console.log(`   … и ещё ${warnings.length - 40}`);
}

if (errors.length) {
  console.log(`\nx  Ошибок: ${errors.length}`);
  errors.slice(0, 40).forEach((e) => console.log(`   • ${e}`));
  if (errors.length > 40) console.log(`   … и ещё ${errors.length - 40}`);
  console.log('\n  Результат: ОШИБКИ — исправьте перед сборкой приложения.\n');
  process.exit(1);
}

console.log(`\n+  Ошибок нет. Проверено:`);
notes.forEach((n) => console.log(`   • ${n}`));
console.log('\n  Результат: данные корректны, можно собирать приложение.\n');
process.exit(0);
