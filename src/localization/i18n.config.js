/**
 * src/localization/i18n.config.js — конфигурация локализации.
 *
 * Правила выбора языка (по техническому заданию):
 *   1) язык выбирается автоматически на основе системного языка устройства;
 *   2) если системный язык не русский и не английский — по умолчанию английский;
 *   3) пользователь может вручную переключить язык в настройках приложения.
 */

/**
 * Поддерживаемые языки интерфейса.
 * icon — имя глифа из набора MaterialCommunityIcons (эмодзи в приложении не используются).
 */
export const LANGUAGES = [
  {
    code: 'ru',
    nativeName: 'Русский',
    englishName: 'Russian',
    icon: 'alpha-r-circle-outline',
  },
  {
    code: 'en',
    nativeName: 'English',
    englishName: 'English',
    icon: 'alpha-e-circle-outline',
  },
];

/** Коды поддерживаемых языков */
export const SUPPORTED_LANGUAGES = LANGUAGES.map((l) => l.code);

/** Язык по умолчанию, если системный язык не поддерживается */
export const DEFAULT_LANGUAGE = 'en';

/** Значение настройки языка «как в системе» */
export const SYSTEM_LANGUAGE = 'system';

/**
 * Определяет язык интерфейса на основе системной локали устройства.
 * @param {string} systemLanguageCode — код языка из expo-localization ('ru', 'en', 'de', …)
 * @returns {string} 'ru' | 'en'
 */
export function resolveSystemLanguage(systemLanguageCode) {
  if (!systemLanguageCode) return DEFAULT_LANGUAGE;
  const code = String(systemLanguageCode).toLowerCase().split('-')[0];
  return SUPPORTED_LANGUAGES.includes(code) ? code : DEFAULT_LANGUAGE;
}

/**
 * Вычисляет фактический язык интерфейса с учётом настройки пользователя.
 * @param {string} preference — 'system' | 'ru' | 'en'
 * @param {string} systemLanguageCode — системный код языка
 */
export function resolveLanguage(preference, systemLanguageCode) {
  if (preference && SUPPORTED_LANGUAGES.includes(preference)) return preference;
  return resolveSystemLanguage(systemLanguageCode);
}
