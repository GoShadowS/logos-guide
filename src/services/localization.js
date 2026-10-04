/**
 * src/services/localization.js — «чистые» функции локализации (без React).
 *
 * translate() поддерживает:
 *   - вложенные ключи: 'navigation.turnLeft'
 *   - подстановку параметров: t('navigation.goUpStairs', { floor: 2 }) → «Поднимитесь на 2 этаж…»
 *   - откат на английский, если ключ не найден в текущем языке
 *   - русские склонения существительных через plural()
 */
import * as Localization from 'expo-localization';
import ru from '../localization/ru.json';
import en from '../localization/en.json';
import { resolveSystemLanguage, resolveLanguage } from '../localization/i18n.config';

export const resources = { ru, en };

/** Получает значение по вложенному ключу ('a.b.c') */
function getByPath(obj, path) {
  return path.split('.').reduce((acc, key) => (acc == null ? undefined : acc[key]), obj);
}

/** Подставляет параметры вида {name} в строку */
function interpolate(template, params) {
  if (!params) return template;
  return template.replace(/\{(\w+)\}/g, (match, name) =>
    Object.prototype.hasOwnProperty.call(params, name) ? String(params[name]) : match
  );
}

/**
 * Перевод ключа на указанный язык.
 * @param {string} language — код языка ('ru' | 'en')
 * @param {string} key — ключ перевода
 * @param {object} [params] — параметры для подстановки
 */
export function translate(language, key, params) {
  let value = getByPath(resources[language] || {}, key);
  if (value === undefined && language !== 'en') {
    value = getByPath(resources.en, key); // откат на английский
  }
  if (typeof value !== 'string') {
    if (__DEV__) console.warn(`[i18n] Ключ не найден: ${key} (${language})`);
    return key;
  }
  return interpolate(value, params);
}

/**
 * Выбор формы слова по числу (с учётом русских правил).
 * Ключи должны иметь суффиксы: One (1), Few (2–4), Many (5+).
 * Пример: plural('ru', 'search.found', 2, '') → 'search.foundFew'
 */
export function plural(language, keyBase, count) {
  let suffix;
  if (language === 'ru') {
    const mod10 = count % 10;
    const mod100 = count % 100;
    if (mod10 === 1 && mod100 !== 11) suffix = 'One';
    else if (mod10 >= 2 && mod10 <= 4 && (mod100 < 10 || mod100 >= 20)) suffix = 'Few';
    else suffix = 'Many';
  } else {
    suffix = count === 1 ? 'One' : 'Many';
  }
  return `${keyBase}${suffix}`;
}

/** Системный код языка устройства ('ru', 'en', …) */
export function getSystemLanguageCode() {
  try {
    const locales = Localization.getLocales();
    return locales && locales.length > 0 ? locales[0].languageCode : null;
  } catch (e) {
    return null;
  }
}

/** Определяет язык интерфейса по системной локали (ru или en) */
export function detectLanguage() {
  return resolveSystemLanguage(getSystemLanguageCode());
}

/** Применяет настройку пользователя к системному языку */
export function resolveUserLanguage(preference) {
  return resolveLanguage(preference, getSystemLanguageCode());
}
