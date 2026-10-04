/**
 * src/theme/index.js — провайдер темы оформления.
 *
 * Поддерживает три режима: 'system' (как в системе), 'light', 'dark'.
 * Выбор пользователя хранится в AsyncStorage (services/storage.js).
 */
import React, { createContext, useContext, useEffect, useMemo, useState } from 'react';
import { useColorScheme } from 'react-native';
import { lightTheme, spacing, radius, typography, brand } from './light';
import { darkTheme } from './dark';
import { loadSettings, saveSettings } from '../services/settings';

const ThemeContext = createContext(null);

/** Собирает полную тему: цвета выбранного режима + общие константы */
function buildTheme(preference, systemScheme) {
  const isDark = preference === 'dark' || (preference === 'system' && systemScheme === 'dark');
  const base = isDark ? darkTheme : lightTheme;
  return {
    ...base,
    isDark,
    spacing,
    radius,
    typography,
    brand,
  };
}

export function ThemeProvider({ children }) {
  const systemScheme = useColorScheme();
  const [preference, setPreferenceState] = useState('system');
  const [ready, setReady] = useState(false);

  // Загружаем сохранённую настройку темы при старте
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const settings = await loadSettings();
      if (cancelled) return;
      if (settings.theme) setPreferenceState(settings.theme);
      setReady(true);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // Меняем тему и сохраняем выбор
  const setPreference = useMemo(
    () => async (next) => {
      setPreferenceState(next);
      await saveSettings({ theme: next });
    },
    []
  );

  const theme = useMemo(() => buildTheme(preference, systemScheme), [preference, systemScheme]);

  const value = useMemo(
    () => ({ theme, preference, setPreference, isDark: theme.isDark, ready }),
    [theme, preference, setPreference, ready]
  );

  if (!ready) return null; // ждём загрузки настроек, чтобы не мигало темой

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

/** Хук доступа к теме: const { theme } = useTheme(); */
export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error('useTheme должен использоваться внутри <ThemeProvider>');
  return ctx;
}

export { lightTheme, darkTheme, spacing, radius, typography, brand };
