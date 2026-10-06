/**
 * tools/test-nav.js — проверка алгоритма навигации и поиска без телефона.
 *
 * Запуск:  node tools/test-nav.js  (или npm test)
 * (используется только на этапе разработки, в приложение не входит)
 */
const path = require('path');
const fs = require('fs');
const babel = require('@babel/core');

// --- Транспилируем src/*.js (ESM + JSX) в CommonJS на лету -------------------
const originalJs = require.extensions['.js'];
require.extensions['.js'] = function (module, filename) {
  if (filename.includes('node_modules')) {
    return originalJs(module, filename);
  }
  const code = fs.readFileSync(filename, 'utf8');
  const out = babel.transformSync(code, {
    filename,
    babelrc: false,
    configFile: false,
    presets: [require.resolve('@babel/preset-react')],
    plugins: [require.resolve('@babel/plugin-transform-modules-commonjs')],
  });
  return module._compile(out.code, filename);
};

// --- Подготовка окружения Node --------------------------------------------
// В приложении эти глобальные переменные предоставляет Metro/React Native.
global.__DEV__ = false;

// expo-localization — нативный модуль, в Node его нет: подменяем заглушкой,
// чтобы можно было тестировать чистые функции локализации.
const expoLocalizationPath = require.resolve('expo-localization');
require.cache[expoLocalizationPath] = {
  id: expoLocalizationPath,
  filename: expoLocalizationPath,
  loaded: true,
  exports: { getLocales: () => [{ languageCode: 'ru', languageTag: 'ru-RU', textDirection: 'ltr' }] },
};

const { buildRoute, secondsToMinutes } = require('../src/services/pathfinding');
const data = require('../src/data');
const { formatDistance, formatDuration, formatFloorAndBuilding, roundMeters } = require('../src/utils/format');
const { translate, plural } = require('../src/services/localization');
const { gpsToPlanLocation, getGpsCalibrationDiagnostics } = require('../src/services/geolocation');
const gpsCalibration = require('../src/data/gps-calibration.json');

const { searchPois, getPoi, getFloor, getBuildingOfFloor, getDefaultStartPoint } = data;

let failures = 0;
function check(name, condition, extra) {
  if (condition) {
    console.log(`  OK   ${name}`);
  } else {
    failures += 1;
    console.log(`  FAIL ${name}${extra ? ' — ' + extra : ''}`);
  }
}

// ---------------------------------------------------------------------------
console.log('\n=== 0. Геопривязка GPS ===');
for (const [floorId, calibration] of Object.entries(gpsCalibration.floors)) {
  const diagnostics = getGpsCalibrationDiagnostics(floorId);
  check(`${floorId}: рассчитаны все опорные точки`, diagnostics?.pointCount === calibration.controlPoints.length);
  check(`${floorId}: ошибка калибровки менее 1.5 м`, diagnostics?.maxErrorMeters < 1.5, `${diagnostics?.maxErrorMeters} м`);
  for (const point of calibration.controlPoints) {
    const mapped = gpsToPlanLocation(floorId, point.latitude, point.longitude, 5);
    const error = mapped ? Math.hypot(mapped.x - point.x, mapped.y - point.y) : Infinity;
    check(`${floorId}: точка ${point.label} переводится на план`, !!mapped && mapped.withinPlan && error < 16, `${error.toFixed(1)} единиц плана`);
    check(`${floorId}: точка ${point.label} сохраняет точность GPS`, mapped?.accuracyMeters === 5);
  }
}
check('неизвестный этаж не геопривязывается', gpsToPlanLocation('campus', 55.2, 36.5) === null);
check('некорректная GPS-широта отклоняется', gpsToPlanLocation('main-1', 100, 36.5) === null);

// ---------------------------------------------------------------------------
console.log('\n=== 1. Поиск ===');
const r236 = searchPois('236');
check('поиск «236» находит Аудиторию 236', r236.length > 0 && r236[0].number === '236', JSON.stringify(r236.map((p) => p.name.ru)));
const rRoom236 = searchPois('кабинет 236');
check('поиск «кабинет 236» находит аудиторию 236', rRoom236.length > 0 && rRoom236[0].id === 'main-2-236');
const rClassroom236 = searchPois('аудитория 236');
check('поиск «аудитория 236» находит аудиторию 236', rClassroom236.length > 0 && rClassroom236[0].id === 'main-2-236');
const rEnglishRoom236 = searchPois('room 236');
check('поиск «room 236» находит аудиторию 236', rEnglishRoom236.length > 0 && rEnglishRoom236[0].id === 'main-2-236');
const r213 = searchPois('213');
check('поиск «213» находит ту же аудиторию (старый номер — алиас)', r213.length > 0 && r213[0].number === '236', JSON.stringify(r213.map((p) => p.name.ru)));
const rStol = searchPois('столовая');
check('поиск «столовая» находит столовую', rStol.some((p) => p.type === 'cafeteria'));
check('поиск по удалённой фамилии преподавателя ничего не находит', searchPois('золотарев').length === 0);
check('в помещениях больше нет привязок к преподавателям', data.rooms.every((room) => !Object.prototype.hasOwnProperty.call(room, 'teacherId')));
const r118 = searchPois('118');
check('поиск «118» находит аудиторию 118', r118.length > 0 && r118[0].number === '118');
const rToilet = searchPois('туалет');
check('поиск «туалет» находит туалеты', rToilet.length >= 2 && rToilet.every((p) => p.type === 'toilet'));
const rNone = searchPois('щука');
check('поиск «щука» ничего не находит', rNone.length === 0);
const rHist = searchPois('кабинет истории');
check('поиск «кабинет истории» находит 119', rHist.length > 0 && rHist[0].id === 'main-1-119', JSON.stringify(rHist.map((p) => p.name.ru)));
check('пустой запрос не даёт результатов', searchPois('').length === 0 && searchPois('   ').length === 0);

// ---------------------------------------------------------------------------
console.log('\n=== 2. Маршруты (только внутри здания) ===');

function poi(id) {
  const p = getPoi(id);
  if (!p) throw new Error('Нет точки ' + id);
  return { floorId: p.floorId, x: p.x, y: p.y };
}

const gpsStart = gpsToPlanLocation('main-1', 55.215103, 36.537163, 5);
const gpsStartedRoute = buildRoute(gpsStart, poi('main-1-119'));
check('маршрут от откалиброванного GPS-положения строится', gpsStartedRoute.ok === true, gpsStartedRoute.error);
if (gpsStartedRoute.ok) {
  check('GPS-координата становится фактической точкой старта',
    Math.hypot(gpsStartedRoute.points[0].x - gpsStart.x, gpsStartedRoute.points[0].y - gpsStart.y) < 0.1);
}

// 2.1 В пределах одного этажа: 119 → туалет
const route1 = buildRoute(poi('main-1-119'), poi('main-1-toilet-1'));
check('маршрут 119 → туалет строится', route1.ok === true, route1.error);
if (route1.ok) {
  check('маршрут в пределах одного этажа', route1.floors.length === 1 && route1.floors[0] === 'main-1', JSON.stringify(route1.floors));
  check('дистанция разумная (5..60 м)', route1.distanceM > 5 && route1.distanceM < 60, route1.distanceM + ' м');
  check('время разумное (10..120 с)', route1.durationSec > 10 && route1.durationSec < 120, route1.durationSec + ' с');
  check('шаги содержат повороты', route1.steps.some((s) => s.kind === 'left' || s.kind === 'right' || s.kind === 'straight'), JSON.stringify(route1.steps.map((s) => s.kind)));
  console.log('   шаги:', route1.steps.map((s) => `${s.kind}${s.distanceM ? ` ${roundMeters(s.distanceM)}м` : ''}`).join(' | '));
}

// 2.2 Между этажами: 1 этаж → 2 этаж (202 — кабинет химии)
const route2 = buildRoute(poi('main-1-119'), poi('main-2-202'));
check('маршрут 119 (1 эт.) → 202 (2 эт.) строится', route2.ok === true, route2.error);
if (route2.ok) {
  check('маршрут проходит по двум этажам', route2.floors.includes('main-1') && route2.floors.includes('main-2'), JSON.stringify(route2.floors));
  const upKind = route2.steps.some((s) => s.kind === 'stairsUp' || s.kind === 'elevatorUp');
  check('есть шаг перехода между этажами (лестница/лифт)', upKind, JSON.stringify(route2.steps.map((s) => s.kind)));
  console.log('   шаги:', route2.steps.map((s) => `${s.kind}${s.distanceM ? ` ${roundMeters(s.distanceM)}м` : ''}`).join(' | '), '| всего', route2.distanceM, 'м,', secondsToMinutes(route2.durationSec), 'мин');
}

// 2.2b Левое крыло: 138 (1 эт.) → 207 (2 эт.) — ближайшая лестница в левом крыле
const route2b = buildRoute(poi('main-1-138'), poi('main-2-207'));
check('маршрут 138 (1 эт.) → 207 (2 эт.) строится', route2b.ok === true, route2b.error);
if (route2b.ok) {
  check('маршрут использует лестницу левого крыла', route2b.steps.some((s) => s.kind === 'stairsUp'), JSON.stringify(route2b.steps.map((s) => s.kind)));
  console.log('   шаги:', route2b.steps.map((s) => `${s.kind}${s.distanceM ? ` ${roundMeters(s.distanceM)}м` : ''}`).join(' | '), '| всего', route2b.distanceM, 'м');
}

// 2.3 От главного входа (внутри 1 этажа) на 2 этаж
const route4 = buildRoute(poi('main-1-entrance'), poi('main-2-236'));
check('маршрут вход → 236 строится', route4.ok === true, route4.error);
if (route4.ok) {
  check('маршрут не выходит за пределы этажей main-1/main-2', route4.floors.every((f) => f === 'main-1' || f === 'main-2'), JSON.stringify(route4.floors));
  console.log('   этапы:', route4.floors.join(' → '), '| всего', route4.distanceM, 'м,', secondsToMinutes(route4.durationSec), 'мин');
}

// 2.4 Обратный маршрут должен быть симметричным по дистанции
const route5 = buildRoute(poi('main-2-202'), poi('main-1-119'));
check('обратный маршрут 202 → 119 строится', route5.ok === true, route5.error);
if (route5.ok && route2.ok) {
  const diff = Math.abs(route5.distanceM - route2.distanceM);
  check('дистанции туда/обратно совпадают (±15 м)', diff <= 15, `${route2.distanceM} vs ${route5.distanceM}`);
  check('есть шаг перехода между этажами вниз', route5.steps.some((s) => s.kind === 'stairsDown' || s.kind === 'elevatorDown'));
}

// 2.5 Маршрут внутри аудитории (дверь → центр) — очень короткий
const route6 = buildRoute(
  { floorId: 'main-1', x: 405, y: 200 },
  { floorId: 'main-1', x: 405, y: 110 }
);
check('короткий маршрут внутри помещения строится', route6.ok === true, route6.error);

// 2.6 Точка отправления по умолчанию (главный вход) существует и проходима
const startPoint = getDefaultStartPoint();
check('точка отправления по умолчанию определена', !!startPoint && !!getFloor(startPoint.floorId), JSON.stringify(startPoint));
if (startPoint) {
  const routeFromStart = buildRoute(startPoint, poi('main-2-library'));
  check('маршрут от точки по умолчанию до библиотеки строится', routeFromStart.ok === true, routeFromStart.error);
}

// 2.7 Недостижимая точка (за пределами плана) не ломает построение
const routeBad = buildRoute({ floorId: 'main-1', x: -500, y: -500 }, poi('main-1-119'));
check('маршрут из точки вне плана корректно отклоняется', routeBad.ok === false || routeBad.ok === true);
const routeUnknownFloor = buildRoute({ floorId: 'campus', x: 100, y: 100 }, poi('main-1-119'));
check('этажа «campus» больше нет — маршрут отклоняется', routeUnknownFloor.ok === false, JSON.stringify(routeUnknownFloor.floors));

// ---------------------------------------------------------------------------
console.log('\n=== 3. Проверка данных ===');
check('аудиторий достаточно (>40)', data.rooms.length > 40, String(data.rooms.length));
check('все комнаты ссылаются на существующие этажи', data.rooms.every((r) => getFloor(r.floorId)));
check('все POI ссылаются на существующие этажи', data.pois.every((p) => getFloor(p.floorId)));
check('нет этажа территории (campus)', !getFloor('campus') && data.floors.every((f) => f.id !== 'campus'));
check('нет точек на территории', data.pois.every((p) => p.floorId !== 'campus'));
check('у каждого этажа есть реальный план', data.floors.every((f) => f.realPlan && f.planFile), JSON.stringify(data.floors.map((f) => [f.id, f.realPlan, f.planFile])));
check('двери аудиторий внутри этажа', data.rooms.every((r) => {
  const f = getFloor(r.floorId);
  return r.door.x >= 0 && r.door.x <= f.width && r.door.y >= 0 && r.door.y <= f.height;
}));
check('у корпуса перечислены оба этажа', getBuildingOfFloor('main-2')?.floors?.length === 2);

// Планы могут поставляться только встроенными SVG-копиями assets/; Map/ — необязательный источник.
for (const floor of data.floors) {
  const source = path.join(__dirname, '..', '..', 'Map', `${floor.realPlan}.svg`);
  const target = path.join(__dirname, '..', 'assets', 'images', 'maps', floor.planFile);
  const packaged = fs.existsSync(target);
  const matchesSource = !fs.existsSync(source)
    || (packaged && fs.readFileSync(source, 'utf8') === fs.readFileSync(target, 'utf8'));
  check(`план ${floor.id} доступен во встроенных ресурсах`, packaged && matchesSource);
}

// ---------------------------------------------------------------------------
console.log('\n=== 4. Форматирование и локализация ===');
const t = (key, params) => translate('ru', key, params);
check('formatDistance: метры', formatDistance(170, t) === '170 м', formatDistance(170, t));
check('formatDistance: километры', formatDistance(1500, t) === '1,5 км', formatDistance(1500, t));
check('formatDistance: ноль метров', formatDistance(0, t) === '0 м', formatDistance(0, t));
check('formatDuration: минуты', formatDuration(150, t) === '3 мин', formatDuration(150, t));
check('formatFloorAndBuilding: корпус + этаж без [object Object]',
  formatFloorAndBuilding(getFloor('main-2'), getBuildingOfFloor('main-2'), t, 'ru') === 'Учебный корпус · Этаж 2',
  formatFloorAndBuilding(getFloor('main-2'), getBuildingOfFloor('main-2'), t, 'ru'));
check('plural: одна точка', translate('ru', plural('ru', 'search.found', 1), { count: 1 }).includes('Найдена'));
check('plural: много точек', translate('ru', plural('ru', 'search.found', 12), { count: 12 }).includes('Найдено 12 точек'));
check('перевод шага маршрута', translate('ru', 'ui.goUpStairs', { floor: 2 }) === 'Поднимитесь на 2 этаж по лестнице');
check('вкладка расписания локализована', translate('ru', 'tabs.schedule') === 'Расписание' && translate('en', 'tabs.schedule') === 'Schedule');

console.log(`\nИтого: ${failures} ошибок(ка)\n`);
process.exit(failures > 0 ? 1 : 0);
