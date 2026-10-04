/**
 * src/services/storage.js — локальное хранилище (AsyncStorage).
 *
 * Хранит:
 *   - настройки пользователя (язык, тема)
 *   - избранные места
 *   - историю поиска
 *   - последний открытый этаж
 */
import AsyncStorage from '@react-native-async-storage/async-storage';
import { config } from '../data';

/** Ключи хранилища */
export const STORAGE_KEYS = {
  settings: '@logos_guide/settings/v1',
  favorites: '@logos_guide/favorites/v1',
  searchHistory: '@logos_guide/search_history/v1',
  lastFloor: '@logos_guide/last_floor/v1',
};

// ---------------------------------------------------------------------------
// Низкоуровневые helpers
// ---------------------------------------------------------------------------

async function readJson(key, fallback) {
  try {
    const raw = await AsyncStorage.getItem(key);
    if (!raw) return fallback;
    return JSON.parse(raw);
  } catch (error) {
    console.warn('[storage] Ошибка чтения', key, error);
    return fallback;
  }
}

async function writeJson(key, value) {
  try {
    await AsyncStorage.setItem(key, JSON.stringify(value));
    return true;
  } catch (error) {
    console.warn('[storage] Ошибка записи', key, error);
    return false;
  }
}

// ---------------------------------------------------------------------------
// Избранное
// ---------------------------------------------------------------------------

/** Список id избранных точек */
export async function getFavorites() {
  const list = await readJson(STORAGE_KEYS.favorites, []);
  return Array.isArray(list) ? list : [];
}

export async function isFavorite(id) {
  const list = await getFavorites();
  return list.includes(id);
}

/** Добавляет/убирает точку из избранного. Возвращает новый список. */
export async function toggleFavorite(id) {
  const list = await getFavorites();
  const next = list.includes(id) ? list.filter((item) => item !== id) : [id, ...list];
  await writeJson(STORAGE_KEYS.favorites, next);
  return next;
}

export async function setFavorites(ids) {
  await writeJson(STORAGE_KEYS.favorites, ids);
  return ids;
}

// ---------------------------------------------------------------------------
// История поиска
// ---------------------------------------------------------------------------

const historyLimit = config?.search?.historyLimit || 20;

export async function getSearchHistory() {
  const list = await readJson(STORAGE_KEYS.searchHistory, []);
  return Array.isArray(list) ? list : [];
}

/** Добавляет запрос в начало истории (без дубликатов) */
export async function addSearchHistory(query) {
  const trimmed = String(query || '').trim();
  if (!trimmed) return getSearchHistory();
  const list = await getSearchHistory();
  const next = [trimmed, ...list.filter((item) => item !== trimmed)].slice(0, historyLimit);
  await writeJson(STORAGE_KEYS.searchHistory, next);
  return next;
}

export async function removeSearchHistory(query) {
  const list = await getSearchHistory();
  const next = list.filter((item) => item !== query);
  await writeJson(STORAGE_KEYS.searchHistory, next);
  return next;
}

export async function clearSearchHistory() {
  await writeJson(STORAGE_KEYS.searchHistory, []);
  return [];
}

// ---------------------------------------------------------------------------
// Последний открытый этаж
// ---------------------------------------------------------------------------

export async function getLastFloor() {
  return readJson(STORAGE_KEYS.lastFloor, config?.defaults?.startFloorId || 'main-1');
}

export async function setLastFloor(floorId) {
  await writeJson(STORAGE_KEYS.lastFloor, floorId);
  return floorId;
}
