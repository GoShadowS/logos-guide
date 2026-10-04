/**
 * src/utils/format.js — форматирование чисел, расстояний и времени.
 * Функции принимают функцию перевода t, чтобы подставлять единицы измерения
 * на нужном языке («170 м» / «170 m»).
 */

/** «170 м» или «1,2 км» */
export function formatDistance(meters, t) {
  if (meters == null || Number.isNaN(meters)) return '';
  if (meters < 1) return `0 ${t('ui.m')}`;
  if (meters < 1000) return `${Math.round(meters)} ${t('ui.m')}`;
  const km = meters / 1000;
  const value = km >= 10 ? Math.round(km) : km.toFixed(1).replace('.', ',');
  return `${value} ${t('ui.km')}`;
}

/** «2 мин», «1 ч 5 мин», «<1 мин» */
export function formatDuration(seconds, t) {
  if (seconds == null || Number.isNaN(seconds) || seconds <= 0) {
    return '<1 ' + t('ui.minutesShort', { count: '' }).trim();
  }
  if (seconds < 60) return t('ui.lessThanMinute');
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return t('ui.minutesShort', { count: minutes });
  const hours = Math.floor(minutes / 60);
  const restMinutes = minutes % 60;
  if (restMinutes === 0) return t('ui.hoursShort', { count: hours });
  return `${t('ui.hoursShort', { count: hours })} ${t('ui.minutesShort', { count: restMinutes })}`;
}

/** Округление метров до целого (для подписей шагов) */
export function roundMeters(meters) {
  if (meters == null || Number.isNaN(meters)) return 0;
  return Math.round(meters);
}

/** «Учебный корпус · Этаж 2» */
export function formatFloorAndBuilding(floor, building, t, language = 'ru') {
  const parts = [];
  if (building) parts.push(building.name?.[language] || building.name?.ru || '');
  if (floor) parts.push(t('ui.floor') + ' ' + floor.level);
  return parts.filter(Boolean).join(' · ');
}
