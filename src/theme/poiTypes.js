/**
 * src/theme/poiTypes.js — описание типов точек интереса (POI):
 * иконка, цвет маркера, ключ перевода, категория для «быстрого поиска».
 *
 * Иконки — из набора MaterialCommunityIcons (пакет @expo/vector-icons).
 */

export const POI_TYPES = {
  classroom: { icon: 'school', labelKey: 'poiType.classroom', category: 'rooms', marker: 'primary' },
  lab: { icon: 'flask', labelKey: 'poiType.lab', category: 'rooms', marker: 'primary' },
  office: { icon: 'briefcase', labelKey: 'poiType.office', category: 'rooms', marker: 'primary' },
  library: { icon: 'book', labelKey: 'poiType.library', category: 'places', marker: 'violet' },
  cafeteria: { icon: 'silverware-fork-knife', labelKey: 'poiType.cafeteria', category: 'food', marker: 'orange' },
  toilet: { icon: 'toilet', labelKey: 'poiType.toilet', category: 'toilets', marker: 'teal' },
  wardrobe: { icon: 'hanger', labelKey: 'poiType.wardrobe', category: 'places', marker: 'blue' },
  medical: { icon: 'medical-bag', labelKey: 'poiType.medical', category: 'places', marker: 'red' },
  hall: { icon: 'theater', labelKey: 'poiType.hall', category: 'places', marker: 'violet' },
  stairs: { icon: 'stairs', labelKey: 'poiType.stairs', category: 'stairs', marker: 'indigo' },
  elevator: { icon: 'elevator', labelKey: 'poiType.elevator', category: 'stairs', marker: 'indigo' },
  entrance: { icon: 'door-open', labelKey: 'poiType.entrance', category: 'entrances', marker: 'green' },
  parking: { icon: 'parking', labelKey: 'poiType.parking', category: 'parking', marker: 'gray' },
  atm: { icon: 'cash', labelKey: 'poiType.atm', category: 'services', marker: 'gray' },
  vending: { icon: 'coffee', labelKey: 'poiType.vending', category: 'food', marker: 'orange' },
  transport: { icon: 'bus', labelKey: 'poiType.transport', category: 'services', marker: 'gray' },
  security: { icon: 'shield-account', labelKey: 'poiType.security', category: 'security', marker: 'red' },
};

/** Цвета маркеров по «роли» (светлая / тёмная тема) */
const MARKER_COLORS = {
  primary: { light: '#1A18E0', dark: '#4E7BFF' },
  violet: { light: '#7A3FE0', dark: '#A97BFF' },
  orange: { light: '#E07B1A', dark: '#FFA84D' },
  teal: { light: '#0E8F8F', dark: '#2FD3D3' },
  blue: { light: '#2E7CF6', dark: '#5B9BFF' },
  red: { light: '#D63030', dark: '#FF6B6F' },
  indigo: { light: '#4B3FD0', dark: '#8B7BFF' },
  green: { light: '#0E9F5A', dark: '#29C46F' },
  gray: { light: '#5A6172', dark: '#9AA0B4' },
};

/** Цвет маркера для типа POI с учётом темы */
export function getMarkerColor(type, isDark) {
  const meta = POI_TYPES[type] || POI_TYPES.classroom;
  const role = meta.marker || 'primary';
  const pair = MARKER_COLORS[role] || MARKER_COLORS.primary;
  return isDark ? pair.dark : pair.light;
}

/** Плитки «Быстрый поиск» на экране поиска */
export const QUICK_CATEGORIES = [
  { id: 'rooms', icon: 'office-building', labelKey: 'quick.rooms', types: ['classroom', 'lab', 'office'] },
  { id: 'entrances', icon: 'door-open', labelKey: 'quick.entrances', types: ['entrance'] },
  { id: 'toilets', icon: 'toilet', labelKey: 'quick.toilets', types: ['toilet'] },
  { id: 'security', icon: 'shield-account', labelKey: 'quick.security', types: ['security'] },
  { id: 'food', icon: 'food-fork-drink', labelKey: 'quick.food', types: ['cafeteria', 'vending'] },
  { id: 'stairs', icon: 'stairs', labelKey: 'quick.stairs', types: ['stairs', 'elevator'] },
];

/** Категории-фильтры на карте; широкие элементы повторяют быстрые действия из макета. */
export const MAP_FILTERS = [
  { id: 'rooms', icon: 'office-building', labelKey: 'quick.rooms', types: ['classroom', 'lab', 'office'] },
  { id: 'stairs', icon: 'stairs', labelKey: 'quick.stairs', types: ['stairs', 'elevator'] },
  { id: 'toilets', icon: 'toilet', labelKey: 'quick.toilets', types: ['toilet'] },
  { id: 'food', icon: 'food-fork-drink', labelKey: 'quick.food', types: ['cafeteria', 'vending'] },
  { id: 'all', icon: 'layers-outline', labelKey: 'filter.all', types: null },
  { id: 'entrances', icon: 'door-open', labelKey: 'quick.entrances', types: ['entrance'] },
];
