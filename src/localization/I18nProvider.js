/**
 * src/localization/I18nProvider.js — React-контекст локализации.
 *
 * Отдаёт в приложение:
 *   language   — фактический код языка ('ru' | 'en')
 *   preference — настройка пользователя ('system' | 'ru' | 'en')
 *   t(key, params)      — перевод строки
 *   tp(keyBase, count)  — перевод с учётом склонений («Найдено 2 кабинета»)
 *   setLanguage(next)   — смена языка с сохранением в настройки
 */
import React, { createContext, useContext, useEffect, useMemo, useState, useCallback } from 'react';
import { translate, plural, getSystemLanguageCode } from '../services/localization';
import { resolveLanguage } from './i18n.config';
import { loadSettings, saveSettings } from '../services/settings';

const I18nContext = createContext(null);

export function I18nProvider({ children }) {
  const [preference, setPreference] = useState('system');
  const [ready, setReady] = useState(false);

  // Читаем сохранённую настройку языка
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const settings = await loadSettings();
      if (cancelled) return;
      setPreference(settings.language || 'system');
      setReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const systemCode = useMemo(() => getSystemLanguageCode(), []);
  const language = useMemo(() => resolveLanguage(preference, systemCode), [preference, systemCode]);

  const t = useCallback((key, params) => translate(language, key, params), [language]);

  const tp = useCallback(
    (keyBase, count, params) => translate(language, plural(language, keyBase, count), { count, ...params }),
    [language]
  );

  const setLanguage = useCallback(async (next) => {
    setPreference(next);
    await saveSettings({ language: next });
  }, []);

  const value = useMemo(
    () => ({ language, preference, setLanguage, t, tp, ready }),
    [language, preference, setLanguage, t, tp, ready]
  );

  if (!ready) return null;

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

/** Хук локализации: const { t, language } = useI18n(); */
export function useI18n() {
  const ctx = useContext(I18nContext);
  if (!ctx) throw new Error('useI18n должен использоваться внутри <I18nProvider>');
  return ctx;
}
