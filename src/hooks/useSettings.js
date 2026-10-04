/**
 * hooks/useSettings.js — доступ к настройкам приложения (язык, тема).
 */
import { useCallback, useEffect, useState } from 'react';
import { loadSettings, saveSettings, resetSettings, DEFAULT_SETTINGS } from '../services/settings';

export function useSettings() {
  const [settings, setSettings] = useState(DEFAULT_SETTINGS);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const loaded = await loadSettings();
      if (!cancelled) {
        setSettings(loaded);
        setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const update = useCallback(async (patch) => {
    const next = await saveSettings(patch);
    setSettings(next);
    return next;
  }, []);

  const reset = useCallback(async () => {
    const next = await resetSettings();
    setSettings(next);
    return next;
  }, []);

  return { settings, update, reset, ready };
}

export default useSettings;
