/**
 * src/data/index.js — единый слой доступа к данным карт.
 *
 * Здесь собирается «индекс» всех точек, по которым работает поиск и навигация:
 * аудитории (rooms.json) + точки интереса (poi.json) + преподаватели (teachers.json).
 * Каждая точка приводится к единому виду:
 *   { id, kind, type, number, name: {ru,en}, floorId, x, y, buildingId,
 *     teacherId, aliases: [...], description?: {ru,en} }
 */
import floorsData from './floors.json';
import roomsData from './rooms.json';
import poiData from './poi.json';
import teachersData from './teachers.json';
import buildingsData from './buildings.json';
import configData from './config.json';

// ---------------------------------------------------------------------------
// Нормализация поисковых строк
// ---------------------------------------------------------------------------

/** Приводит строку к виду, удобному для поиска: нижний регистр, ё→е, без лишних пробелов */
export function normalizeText(value) {
  if (value == null) return '';
  return String(value)
    .toLowerCase()
    .replace(/ё/g, 'е')
    .replace(/[«»"']/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

// ---------------------------------------------------------------------------
// Базовые коллекции
// ---------------------------------------------------------------------------

export const floors = floorsData.floors;
export const rooms = roomsData.rooms;
export const pois = poiData.pois;
export const teachers = teachersData.teachers;
export const buildings = buildingsData.buildings;
export const config = configData;

/** Масштаб планировки по умолчанию (единицы viewBox на метр) */
export const DEFAULT_UNITS_PER_METER = floorsData.unitsPerMeter;

const floorsById = new Map(floors.map((f) => [f.id, f]));
const roomsById = new Map(rooms.map((r) => [r.id, r]));
const teachersById = new Map(teachers.map((t) => [t.id, t]));
const buildingsById = new Map(buildings.map((b) => [b.id, b]));

export function getFloor(floorId) {
  return floorsById.get(floorId) || null;
}

/** Масштаб конкретного этажа: единицы плана в одном метре (unitsPerMeter из floors.json) */
export function getFloorUnitsPerMeter(floorId) {
  const floor = getFloor(floorId);
  if (!floor) return DEFAULT_UNITS_PER_METER;
  return floor.unitsPerMeter || DEFAULT_UNITS_PER_METER;
}

export function getRoom(roomId) {
  return roomsById.get(roomId) || null;
}

export function getTeacher(teacherId) {
  return teachersById.get(teacherId) || null;
}

export function getBuilding(buildingId) {
  return buildingsById.get(buildingId) || null;
}

export function getBuildingOfFloor(floorId) {
  const floor = getFloor(floorId);
  if (!floor || !floor.buildingId) return null;
  return buildingsById.get(floor.buildingId) || null;
}

/** Все этажи здания (снизу вверх) */
export function getFloorsOfBuilding(buildingId) {
  const building = buildingsById.get(buildingId);
  if (!building) return [];
  return building.floors.map((id) => floorsById.get(id)).filter(Boolean);
}

/** Помещения, расположенные на этаже */
export function getRoomsOfFloor(floorId) {
  return rooms.filter((r) => r.floorId === floorId);
}

/** POI, расположенные на этаже */
export function getPoisOfFloor(floorId) {
  return pois.filter((p) => p.floorId === floorId);
}

/** Следующий/предыдущий этаж здания (для переключателя этажей) */
export function getAdjacentFloor(floorId, direction) {
  const building = getBuildingOfFloor(floorId);
  if (!building) return null;
  const index = building.floors.indexOf(floorId);
  if (index === -1) return null;
  const nextIndex = index + direction;
  if (nextIndex < 0 || nextIndex >= building.floors.length) return null;
  return getFloor(building.floors[nextIndex]);
}

// ---------------------------------------------------------------------------
// Единый индекс точек (POI)
// ---------------------------------------------------------------------------

/** Центр помещения в координатах этажа */
function roomCenter(room) {
  return { x: room.x + room.w / 2, y: room.y + room.h / 2 };
}

const poiIndex = [];

for (const room of rooms) {
  const center = roomCenter(room);
  poiIndex.push({
    id: room.id,
    kind: 'room',
    type: room.type,
    number: room.number || null,
    name: room.name,
    description: room.description || null,
    floorId: room.floorId,
    buildingId: room.buildingId || null,
    x: center.x,
    y: center.y,
    door: room.door || null,
    teacherId: room.teacherId || null,
    aliases: room.aliases || [],
  });
}

for (const poi of pois) {
  poiIndex.push({
    id: poi.id,
    kind: 'poi',
    type: poi.type,
    number: null,
    name: poi.name,
    description: poi.description || null,
    floorId: poi.floorId,
    buildingId: getFloor(poi.floorId)?.buildingId || null,
    x: poi.x,
    y: poi.y,
    door: null,
    teacherId: null,
    aliases: poi.aliases || [],
  });
}

const poiById = new Map(poiIndex.map((p) => [p.id, p]));

export function getPoi(id) {
  return poiById.get(id) || null;
}

export function getAllPois() {
  return poiIndex;
}

/** Точка, соответствующая аудитории преподавателя */
export function getPoiOfTeacher(teacherId) {
  const teacher = teachersById.get(teacherId);
  if (!teacher) return null;
  return poiById.get(teacher.roomId) || null;
}

/**
 * Точка отправления по умолчанию — главный вход в здание (помещение типа entrance
 * на первом этаже). Используется всеми экранами, где маршрут строится «от входа».
 * @returns {{floorId: string, x: number, y: number, label: string}|null}
 */
export function getDefaultStartPoint() {
  const entrance = poiIndex.find((item) => item.id === 'main-1-entrance');
  if (!entrance) return null;
  return {
    floorId: entrance.floorId,
    x: entrance.x,
    y: entrance.y,
    label: entrance.name?.ru || '',
  };
}

// ---------------------------------------------------------------------------
// Поиск
// ---------------------------------------------------------------------------

/**
 * Оценка релевантности точки для поискового запроса.
 * Чем больше — тем выше в результатах. 0 = не подходит.
 */
function scorePoi(poi, query) {
  const number = poi.number ? normalizeText(poi.number) : '';
  const nameRu = normalizeText(poi.name?.ru);
  const nameEn = normalizeText(poi.name?.en);
  const aliases = (poi.aliases || []).map(normalizeText);
  const teacher = poi.teacherId ? teachersById.get(poi.teacherId) : null;
  const teacherName = teacher ? normalizeText(teacher.name?.ru) : '';
  const teacherSubject = teacher ? normalizeText(teacher.subject?.ru) : '';
  const teacherAliases = teacher ? (teacher.aliases || []).map(normalizeText) : [];

  let score = 0;

  // Точное совпадение номера аудитории — самый высокий приоритет
  if (number && query === number) score = Math.max(score, 100);
  else if (number && number.startsWith(query)) score = Math.max(score, 85);
  else if (number && number.includes(query)) score = Math.max(score, 60);

  if (nameRu === query || nameEn === query) score = Math.max(score, 95);
  else if (nameRu.startsWith(query) || nameEn.startsWith(query)) score = Math.max(score, 80);
  else if (nameRu.includes(query) || nameEn.includes(query)) score = Math.max(score, 55);

  for (const alias of aliases) {
    if (alias === query) score = Math.max(score, 75);
    else if (alias.startsWith(query)) score = Math.max(score, 65);
    else if (alias.includes(query)) score = Math.max(score, 45);
  }

  // Поиск по преподавателю и предмету
  if (teacherName === query) score = Math.max(score, 70);
  else if (teacherName.includes(query)) score = Math.max(score, 50);
  if (teacherSubject === query) score = Math.max(score, 68);
  else if (teacherSubject.includes(query)) score = Math.max(score, 48);
  for (const alias of teacherAliases) {
    if (alias === query) score = Math.max(score, 66);
    else if (alias.includes(query)) score = Math.max(score, 46);
  }

  // Match words across combined fields so "кабинет 236" finds room aliases and number.
  const tokens = query.split(' ').filter(Boolean);
  if (tokens.length > 1) {
    const textFields = [nameRu, nameEn, ...aliases, teacherName, teacherSubject, ...teacherAliases]
      .filter(Boolean);
    const allMatched = tokens.every((token) =>
      number.includes(token) || textFields.some((field) => field.includes(token))
    );
    if (allMatched) {
      const numberToken = [...tokens].reverse().find((token) => number.startsWith(token));
      score = Math.max(score, numberToken ? 90 : 40);
    }
  }

  return score;
}

/**
 * Поиск точек по запросу.
 * @returns {Array} отсортированный по релевантности список точек
 */
export function searchPois(query, limit = 20) {
  const q = normalizeText(query);
  if (!q) return [];

  const results = [];
  for (const poi of poiIndex) {
    const score = scorePoi(poi, q);
    if (score > 0) results.push({ poi, score });
  }

  results.sort((a, b) => {
    if (b.score !== a.score) return b.score - a.score;
    // При равном.score — нумерованные аудитории выше, сортировка по номеру
    if (!!a.poi.number !== !!b.poi.number) return a.poi.number ? -1 : 1;
    return String(a.poi.name?.ru || '').localeCompare(String(b.poi.name?.ru || ''), 'ru');
  });

  return results.slice(0, limit).map((r) => r.poi);
}

/** Автодополнение: быстрое предложение по первым буквам */
export function autocompletePois(query, limit = 8) {
  return searchPois(query, limit);
}

/** Быстрые категории для экрана поиска */
export function getPoisByType(type) {
  return poiIndex.filter((p) => p.type === type);
}

export function getPoisByTypes(types) {
  const set = new Set(types);
  return poiIndex.filter((p) => set.has(p.type));
}
