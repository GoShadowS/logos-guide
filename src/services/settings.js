/**
 * src/services/settings.js — настройки пользователя.
 *
 * Настройки:
 *   language — 'system' | 'ru' | 'en'
 *   theme    — 'system' | 'light' | 'dark'
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import { STORAGE_KEYS } from './storage';
import { config } from '../data';

/** Значения по умолчанию */
export const DEFAULT_SETTINGS = {
  language: config?.defaults?.language || 'system',
  theme: config?.defaults?.theme || 'system',
};

/** Читает настройки, подмешивая значения по умолчанию */
export async function loadSettings() {
  try {
    const raw = await AsyncStorage.getItem(STORAGE_KEYS.settings);
    if (!raw) return { ...DEFAULT_SETTINGS };
    const parsed = JSON.parse(raw);
    // Берём только известные поля: старые записи (например, удалённый mapMode) игнорируются
    return Object.keys(DEFAULT_SETTINGS).reduce((acc, key) => {
      acc[key] = parsed[key] ?? DEFAULT_SETTINGS[key];
      return acc;
    }, {});
  } catch (error) {
    console.warn('[settings] Ошибка чтения настроек', error);
    return { ...DEFAULT_SETTINGS };
  }
}

/** Сохраняет часть настроек (merge) */
export async function saveSettings(patch) {
  const current = await loadSettings();
  const next = { ...current, ...patch };
  try {
    await AsyncStorage.setItem(STORAGE_KEYS.settings, JSON.stringify(next));
  } catch (error) {
    console.warn('[settings] Ошибка сохранения настроек', error);
  }
  return next;
}

/** Сбрасывает настройки к значениям по умолчанию */
export async function resetSettings() {
  try {
    await AsyncStorage.setItem(STORAGE_KEYS.settings, JSON.stringify(DEFAULT_SETTINGS));
  } catch (error) {
    console.warn('[settings] Ошибка сброса настроек', error);
  }
  return { ...DEFAULT_SETTINGS };
}
