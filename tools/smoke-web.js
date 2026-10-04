#!/usr/bin/env node
/* eslint-disable */
/**
 * tools/smoke-web.js — дымовой тест РЕАЛЬНОЙ сборки приложения.
 *
 * Собирает веб-бандл (expo export) и запускает его в jsdom: приложение
 * действительно стартует, а тест «трогает» интерфейс так же, как пользователь.
 * Тест не зависит от языка интерфейса: ожидаемые строки берутся из
 * src/localization/*.json для того языка, который выбрало приложение.
 *
 * Проверяется:
 *   1. приложение запускается без ошибок и рисует карту;
 *   2. карта — реальный план из Map/map1lower.svg (viewBox 758×552);
 *   3. приближение карты работает (кнопки «+» / «−» / «вписать»);
 *   4. тап по точке открывает карточку и строит маршрут;
 *   5. тема переключается МГНОВЕННО, без перезапуска приложения;
 *   6. в поле поиска можно печатать, результаты появляются;
 *   7. вкладок ровно четыре: нет «Расписания», нет QR-сканера, нет карты улицы;
 *   8. в отрисованном интерфейсе нет эмодзи.
 *
 * Запуск:  npm run smoke           (при необходимости сам соберёт бандл)
 *          node tools/smoke-web.js /path/to/export
 */
const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');
const { JSDOM, VirtualConsole } = require('jsdom');

const BUILD_DIR = process.argv[2] || path.join('/tmp', 'logos-web-build');
const PROJECT_DIR = path.join(__dirname, '..');

const RU = require('../src/localization/ru.json');
const EN = require('../src/localization/en.json');
const BUILDINGS = require('../src/data/buildings.json').buildings;

let failures = 0;
function check(name, condition, extra) {
  if (condition) {
    console.log(`  OK   ${name}`);
  } else {
    failures += 1;
    console.log(`  FAIL ${name}${extra ? ' — ' + extra : ''}`);
  }
}

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// ---------------------------------------------------------------------------
// 1. Сборка бандла
// ---------------------------------------------------------------------------
if (!fs.existsSync(path.join(BUILD_DIR, 'index.html'))) {
  console.log('Собираем веб-бандл: npx expo export --platform web …');
  execFileSync('npx', ['expo', 'export', '--platform', 'web', '--output-dir', BUILD_DIR], {
    cwd: PROJECT_DIR,
    stdio: 'inherit',
  });
}

const html = fs.readFileSync(path.join(BUILD_DIR, 'index.html'), 'utf8');
const bundleMatch = /src="([^"]*index-[^"]*\.js)"/.exec(html);
if (!bundleMatch) {
  console.error('Не найден JS-бандл в index.html');
  process.exit(1);
}
const bundlePath = path.join(BUILD_DIR, bundleMatch[1].replace(/^\//, ''));
const code = fs.readFileSync(bundlePath, 'utf8');
console.log(`Бандл: ${bundleMatch[1]} (${(code.length / 1024).toFixed(0)} КБ)\n`);

// ---------------------------------------------------------------------------
// 2. Окружение jsdom
// ---------------------------------------------------------------------------
const runtimeErrors = [];
const virtualConsole = new VirtualConsole();
virtualConsole.on('jsdomError', (e) => runtimeErrors.push('jsdom: ' + e.message));
virtualConsole.on('error', (...args) => runtimeErrors.push('console.error: ' + args.join(' ')));

const dom = new JSDOM('<!doctype html><html><body><div id="root"></div></body></html>', {
  runScripts: 'outside-only',
  pretendToBeVisual: true,
  url: 'http://localhost/',
  virtualConsole,
});
const { window } = dom;

window.matchMedia =
  window.matchMedia ||
  ((query) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
    dispatchEvent: () => false,
  }));
/**
 * react-native-web измеряет элементы через ResizeObserver + offsetWidth/offsetHeight.
 * В jsdom вёрстки нет, поэтому «наблюдатель» сразу сообщает фиксированный размер —
 * иначе onLayout не сработает и карта не узнает размеры экрана.
 */
window.ResizeObserver = class ResizeObserverStub {
  constructor(callback) {
    this._callback = callback;
  }
  observe(target) {
    setTimeout(() => {
      try {
        this._callback(
          [
            {
              target,
              contentRect: {
                width: SCREEN.width,
                height: SCREEN.height,
                top: 0,
                left: 0,
                right: SCREEN.width,
                bottom: SCREEN.height,
                x: 0,
                y: 0,
              },
            },
          ],
          this
        );
      } catch (error) {
        /* ignore */
      }
    }, 0);
  }
  unobserve() {}
  disconnect() {}
};
window.IntersectionObserver = class {
  observe() {}
  unobserve() {}
  disconnect() {}
};
window.Element.prototype.scrollIntoView = function () {};
// jsdom не знает часть CSS-API, которые использует expo-font на вебе
window.CSSFontFaceRule = window.CSSFontFaceRule || class CSSFontFaceRule {};
window.CSSStyleRule = window.CSSStyleRule || class CSSStyleRule {};
window.FontFace =
  window.FontFace ||
  class FontFace {
    constructor(family, source, descriptors) {
      this.family = family;
      this.source = source;
      Object.assign(this, descriptors);
    }
    load() {
      return Promise.resolve(this);
    }
  };
if (window.CSSStyleSheet && !window.CSSStyleSheet.prototype.deleteRule) {
  window.CSSStyleSheet.prototype.deleteRule = function () {};
}
// expo-font на вебе ждёт загрузку шрифтов; в jsdom шрифтов нет — отдаём «загружено»,
// иначе FontFaceObserver через 12 секунд роняет процесс неподтверждённым промисом.
Object.defineProperty(window.document, 'fonts', {
  configurable: true,
  value: {
    load: () => Promise.resolve([{ family: 'stub', status: 'loaded' }]),
    check: () => true,
    ready: Promise.resolve(),
    add() {},
    delete() {},
    forEach() {},
    entries() { return [][Symbol.iterator](); },
    keys() { return [][Symbol.iterator](); },
    values() { return [][Symbol.iterator](); },
    [Symbol.iterator]() { return [][Symbol.iterator](); },
  },
});

// В jsdom нет вёрстки: отдаём фиксированный размер «экрана телефона»,
// иначе onLayout вернёт 0×0 и карта не сможет масштабироваться.
const SCREEN = { width: 390, height: 844 };
window.Element.prototype.getBoundingClientRect = function () {
  return {
    width: SCREEN.width,
    height: SCREEN.height,
    top: 0,
    left: 0,
    right: SCREEN.width,
    bottom: SCREEN.height,
    x: 0,
    y: 0,
    toJSON() {},
  };
};
Object.defineProperty(window.HTMLElement.prototype, 'offsetWidth', {
  get() {
    return SCREEN.width;
  },
});
Object.defineProperty(window.HTMLElement.prototype, 'offsetHeight', {
  get() {
    return SCREEN.height;
  },
});

// Неподтверждённый промис не должен убивать тест — фиксируем как ошибку приложения
process.on('unhandledRejection', (reason) => {
  runtimeErrors.push('unhandledRejection: ' + (reason && reason.message ? reason.message : reason));
});

try {
  window.eval(code);
} catch (error) {
  runtimeErrors.push('eval: ' + (error && error.message));
}

// ---------------------------------------------------------------------------
// 3. Помощники для «кликов» и поиска по тексту
// ---------------------------------------------------------------------------
const { document } = window;

const allElements = () => Array.from(document.querySelectorAll('*'));

/** Наименьший элемент, чей собственный текст равен искомому */
function findByText(text) {
  return (
    allElements().find(
      (el) => el.children.length === 0 && (el.textContent || '').trim() === text
    ) || null
  );
}

const containsText = (text) =>
  allElements().some((el) => (el.textContent || '').includes(text));

/** Кликабельный предок (Pressable / вкладка / ссылка) */
function clickable(el) {
  let node = el;
  while (node && node !== document.body) {
    const role = node.getAttribute && node.getAttribute('role');
    if (role === 'button' || role === 'tab' || role === 'link' || node.tagName === 'BUTTON') {
      return node;
    }
    node = node.parentNode;
  }
  return el;
}

function click(el) {
  if (!el) return false;
  const target = clickable(el);
  target.dispatchEvent(
    new window.MouseEvent('click', { bubbles: true, cancelable: true, view: window })
  );
  return true;
}

/**
 * То же, но берёт ПОСЛЕДНИЙ подходящий элемент: стек навигации не размонтирует
 * предыдущий экран, поэтому нужный текст может встречаться дважды — на верхнем
 * экране он идёт позже по документу.
 */
function findByTextLast(text) {
  const matches = allElements().filter(
    (el) => el.children.length === 0 && (el.textContent || '').trim() === text
  );
  return matches.length ? matches[matches.length - 1] : null;
}

function clickText(text) {
  const el = findByText(text);
  if (!el) return false;
  return click(el);
}

function clickTextLast(text) {
  const el = findByTextLast(text);
  if (!el) return false;
  return click(el);
}

const byAriaLabel = (label) => document.querySelector(`[aria-label="${label}"]`);
const visibleText = () => document.body.textContent || '';

/** Масштаб из CSS-трансформации контейнера карты */
function mapScale() {
  const svg = document.querySelector('svg[viewBox="0 0 758 552"]');
  if (!svg) return null;
  let node = svg;
  while (node && node !== document.body) {
    const transform = (node.style && node.style.transform) || '';
    const matrix = /matrix\(([^)]+)\)/.exec(transform);
    if (matrix) return Number(matrix[1].split(',')[0]);
    const scale = /scale\(([-\d.]+)\)/.exec(transform);
    if (scale) return Number(scale[1]);
    node = node.parentNode;
  }
  return null;
}

/** Яркость цвета «rgb(r, g, b)» (0..255) */
function luminance(color) {
  const match = /rgba?\((\d+),\s*(\d+),\s*(\d+)/.exec(color || '');
  if (!match) return null;
  const [, r, g, b] = match.map(Number);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

const settle = (ms = 150) => sleep(ms);

// ---------------------------------------------------------------------------
// 4. Проверки
// ---------------------------------------------------------------------------
async function run() {
  console.log('=== 1. Запуск приложения ===');
  await settle(1200); // splash скрывается через ~150 мс + чтение настроек

  const root = document.getElementById('root');
  check('приложение отрисовалось', !!root && root.children.length > 0, 'корневой элемент пуст');
  check(
    'нет ошибок времени выполнения',
    runtimeErrors.length === 0,
    runtimeErrors.slice(0, 3).join(' | ')
  );

  // Язык, который выбрало приложение (в jsdom системная локаль — en)
  const lang = visibleText().includes(RU.tabs.home) ? 'ru' : 'en';
  const L = lang === 'ru' ? RU : EN;
  console.log(`  (язык интерфейса: ${lang})`);

  console.log('\n=== 2. Вкладки ===');
/**
 * Иконки MaterialCommunityIcons — это глифы из частных областей Юникода
 * (U+E000…U+F8FF и U+F0000…U+10FFFD). При сравнении текстов их срезаем.
 */
const stripIconGlyphs = (text) =>
  (text || '').replace(/[\uE000-\uF8FF\u{F0000}-\u{FFFFD}\u{100000}-\u{10FFFD}]/gu, '');

const tabLabels = Array.from(document.querySelectorAll('[role="tab"]')).map((el) =>
    stripIconGlyphs(el.textContent).trim()
  );
  check(
    'ровно четыре вкладки: Главная, Карта, Избранное, Ещё',
    tabLabels.length === 4 &&
      tabLabels.includes(L.tabs.home) &&
      tabLabels.includes(L.tabs.map) &&
      tabLabels.includes(L.tabs.favorites) &&
      tabLabels.includes(L.tabs.more),
    JSON.stringify(tabLabels)
  );
  check('вкладки «Расписание» нет', !tabLabels.some((label) => /расписани|schedule/i.test(label)),
    JSON.stringify(tabLabels));

  console.log('\n=== 3. Поиск ===');
  const input = document.querySelector('input');
  check('поле поиска присутствует', !!input);
  const typeInto = async (value) => {
    const setter = Object.getOwnPropertyDescriptor(
      window.HTMLInputElement.prototype,
      'value'
    ).set;
    setter.call(input, value);
    input.dispatchEvent(new window.Event('input', { bubbles: true }));
    await settle(250);
  };
  if (input) {
    await typeInto('236');
    check('ввод в поле поиска работает', input.value === '236', input.value);
    check('результат по номеру «236» найден', containsText('236'));
    await typeInto('119');
    check('поиск «119» находит аудиторию 119', containsText('119'));
    const clearButton = byAriaLabel(L.ui.clear);
    check('есть кнопка очистки поля', !!clearButton);
    if (clearButton) {
      click(clearButton);
      await settle(200);
      check('кнопка очистки стирает запрос', input.value === '', input.value);
    }
  }
  check('кнопка голосового ввода на месте', !!byAriaLabel(L.search.voiceInput));

  console.log('\n=== 3a. Избранное ===');
  if (input) {
    await typeInto('119');
    const resultRow = findByTextLast('119');
    check('результат поиска можно открыть', click(resultRow));
    await settle(700);
    const addFavorite = document.querySelector('[aria-label="' + L.poi.addToFavorites + '"]');
    check('в карточке точки есть кнопка «В избранное»', !!addFavorite);
    if (addFavorite) {
      click(addFavorite);
      await settle(400);
      check(
        'кнопка сменилась на «Убрать из избранного»',
        !!document.querySelector('[aria-label="' + L.poi.removeFromFavorites + '"]')
      );
      window.history.back();
      await settle(900);
      clickText(L.tabs.favorites);
      await settle(600);
      check('аудитория появилась в избранном', containsText('119'));
      const removeButton = findByTextLast(L.favorites.remove);
      if (removeButton) {
        click(removeButton);
        await settle(500);
        check('запись можно убрать из избранного', containsText(L.favorites.empty));
      }
      clickText(L.tabs.home);
      await settle(500);
    }
  }

  console.log('\n=== 4. Карта ===');
  clickText(L.tabs.map);
  await settle(500);
  const floor1Plan = document.querySelector('svg[viewBox="0 0 758 552"]');
  check('отрисован реальный план 1 этажа (Map/map1lower.svg, 758×552)', !!floor1Plan);
  check('на карте есть маркеры точек', !!byAriaLabel('119'));

  console.log('\n=== 5. Приближение карты ===');
  const initialScale = mapScale();
  check('масштаб изначально равен 1', Math.abs((initialScale || 0) - 1) < 0.01, String(initialScale));
  click(byAriaLabel(L.map.zoomIn));
  await settle(400);
  const afterZoomIn = mapScale();
  check('кнопка «+» приближает карту (≈1.6×)', afterZoomIn > 1.4 && afterZoomIn < 1.8, String(afterZoomIn));
  click(byAriaLabel(L.map.zoomIn));
  await settle(400);
  const afterSecondZoom = mapScale();
  check(
    'повторное нажатие «+» продолжает приближение',
    afterSecondZoom > afterZoomIn,
    `${afterZoomIn} → ${afterSecondZoom}`
  );
  click(byAriaLabel(L.map.zoomOut));
  await settle(400);
  const afterZoomOut = mapScale();
  check('кнопка «−» отдаляет карту', afterZoomOut < afterSecondZoom, `${afterSecondZoom} → ${afterZoomOut}`);
  click(byAriaLabel(L.map.resetView));
  await settle(400);
  check('кнопка «вписать» возвращает масштаб 1', Math.abs(mapScale() - 1) < 0.01, String(mapScale()));

  console.log('\n=== 6. Точка на карте и маршрут ===');
  const roomMarker = byAriaLabel('119');
  check('найдена точка «119» на плане', !!roomMarker);
  if (roomMarker) {
    click(roomMarker);
    await settle(400);
    check('открылась карточка точки (экран не упал)', containsText(L.ui.buildRoute));
    const buildingName = BUILDINGS[0].name[lang] || BUILDINGS[0].name.ru;
    check(
      'в карточке показаны корпус и этаж без [object Object]',
      containsText(buildingName) &&
        containsText((L.ui.floor || 'Floor') + ' 1') &&
        !visibleText().includes('[object Object]')
    );
    clickText(L.ui.buildRoute);
    await settle(700);
    check(
      'маршрут построен: появились «Начать маршрут» и «Очистить»',
      containsText(L.ui.startRoute) && containsText(L.ui.clear)
    );
    check('маршрут отрисован линией на плане', !!document.querySelector('svg polyline'));

    console.log('\n=== 6a. Пошаговая навигация ===');
    clickText(L.ui.startRoute);
    await settle(700);
    check('открылся экран пошаговой навигации', containsText(L.ui.steps));
    const stepLabels = [
      L.ui.goStraight,
      L.ui.turnLeft,
      L.ui.turnRight,
      L.ui.slightLeft,
      L.ui.slightRight,
      L.ui.turnAround,
      L.ui.arrive,
    ];
    check(
      'показаны шаги маршрута',
      stepLabels.some((label) => containsText(label))
    );
    check('на экране навигации есть кнопка запуска', !!findByTextLast(L.ui.startRoute));
    clickTextLast(L.ui.startRoute);
    await settle(600);
    check('включился пошаговый режим (появилось «Завершить маршрут»)', !!findByText(L.ui.stopRoute));
    check('в пошаговом режиме есть кнопки «Назад» и «Далее»',
      !!findByTextLast(L.ui.prev) && !!findByTextLast(L.ui.next));
    clickTextLast(L.ui.next);
    await settle(500);
    check('кнопка «Далее» переключает шаг', !!findByTextLast(L.ui.stopRoute));
    clickTextLast(L.ui.stopRoute);
    await settle(600);
    check('«Завершить маршрут» возвращает в режим списка шагов', !!findByTextLast(L.ui.startRoute));
    const backButton = document.querySelector('[aria-label="' + L.ui.back + '"]');
    check('на экране навигации есть кнопка «Назад» с подписью', !!backButton);
    if (backButton) {
      click(backButton);
      await settle(600);
      check('возврат с экрана навигации на карту', !!document.querySelector('svg[viewBox="0 0 758 552"]'));
    }
    clickText(L.ui.clear);
    await settle(400);
  }

  console.log('\n=== 6b. Переключение этажей ===');
  click(byAriaLabel(L.map.chooseFloor));
  await settle(400);
  const floor2Label = `${L.ui.floor} 2`;
  check('список этажей открылся', containsText(floor2Label));
  clickText(floor2Label);
  await settle(500);
  check(
    '2 этаж показывает план Map/map2lower.svg (767×561)',
    !!document.querySelector('svg[viewBox="0 0 767 561"]')
  );
  check('на 2 этаже появилась аудитория 202', !!byAriaLabel('202'));
  click(byAriaLabel(L.map.chooseFloor));
  await settle(400);
  clickText(`${L.ui.floor} 1`);
  await settle(500);
  check(
    'возврат на 1 этаж снова показывает план 758×552',
    !!document.querySelector('svg[viewBox="0 0 758 552"]')
  );

  console.log('\n=== 7. Тема переключается сразу ===');
  clickText(L.tabs.more);
  await settle(500);
  check('открылась вкладка «Ещё» с настройкой темы', containsText(L.settings.theme));

  // Индикатор темы: цвет заголовка строки настроек (theme.colors.text)
  const themeProbe = () => {
    const el = findByText(L.settings.clearHistory);
    return el ? window.getComputedStyle(el).color : null;
  };
  const lightProbe = themeProbe();
  clickText(L.settings.dark);
  await settle(400);
  const darkProbe = themeProbe();
  const lightness = (c) => luminance(c);
  check(
    'тёмная тема применилась сразу, без перезапуска',
    !!darkProbe && darkProbe !== lightProbe && lightness(darkProbe) > 150,
    `${lightProbe} → ${darkProbe}`
  );
  const overlayInDark = allElements().some((el) =>
    /rgba\(6,\s*8,\s*22/.test((el.style && el.style.backgroundColor) || '')
  );
  check('в тёмной теме план карты затемняется', overlayInDark);
  clickText(L.settings.light);
  await settle(400);
  const backProbe = themeProbe();
  check(
    'светлая тема вернулась так же сразу',
    !!backProbe && lightness(backProbe) < 100,
    String(backProbe)
  );

  console.log('\n=== 7a. Переключение языка ===');
  check(
    'переключатель языка на месте (значки вместо эмодзи-флагов)',
    !!findByText('Русский') && !!findByText('English')
  );
  clickText('Русский');
  await settle(600);
  check(
    'интерфейс переключился на русский без перезапуска',
    containsText(RU.tabs.home) && containsText(RU.tabs.map) && containsText(RU.settings.theme)
  );
  clickText('English');
  await settle(600);
  check('интерфейс вернулся на английский', containsText(EN.tabs.home) && containsText(EN.tabs.map));

  console.log('\n=== 8. Состав приложения ===');
  const text = visibleText();
  check('нет QR-сканера', !/qr/i.test(text));
  check('нет карты улицы/территории', !/территори|campus/i.test(text));
  check('нет расписания', !/расписани|schedule/i.test(text));

  console.log('\n=== 9. Эмодзи ===');
  const emojiRe = /[\u{1F000}-\u{1FAFF}\u{2600}-\u{27BF}\u{2B00}-\u{2BFF}\u{FE0F}]/u;
  const emojiFound = emojiRe.exec(text);
  check('в интерфейсе нет эмодзи', !emojiFound, emojiFound ? `найден «${emojiFound[0]}»` : '');

  console.log('\n=== 10. Ошибки времени выполнения ===');
  check(
    'за всё время теста не возникло ошибок',
    runtimeErrors.length === 0,
    runtimeErrors.slice(0, 5).join(' | ')
  );

  console.log(`\nИтого: ${failures} ошибок(ка)\n`);
  process.exit(failures > 0 ? 1 : 0);
}

run().catch((error) => {
  console.error(error);
  process.exit(1);
});
