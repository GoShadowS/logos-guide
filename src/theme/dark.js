/**
 * Тёмная тема оформления.
 * Основные цвета соответствуют тёмным макетам карт из репозитория (папка Map/).
 */

/** Отступы (8-пунктная сетка) — общие для обеих тем */
export const spacing = {
  xs: 4,
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  xxl: 32,
};

/** Скругления углов */
export const radius = {
  sm: 8,
  md: 12,
  lg: 16,
  xl: 24,
  pill: 999,
};

/** Типографика */
export const typography = {
  h1: { fontSize: 26, fontWeight: '700', letterSpacing: 0 },
  h2: { fontSize: 20, fontWeight: '700', letterSpacing: 0 },
  h3: { fontSize: 17, fontWeight: '600' },
  body: { fontSize: 15, fontWeight: '400' },
  bodyStrong: { fontSize: 15, fontWeight: '600' },
  caption: { fontSize: 13, fontWeight: '400' },
  small: { fontSize: 11, fontWeight: '500' },
};

/** Общая палитра бренда (не зависит от темы) */
export const brand = {
  primary: '#1A18E0',
  primaryDark: '#120EA8',
  primaryLight: '#5B57F0',
  primarySoft: '#ECECFE',
  route: '#1A18E0',
  routeSoft: 'rgba(26, 24, 224, 0.12)',
  success: '#0E9F5A',
  warning: '#E8A33D',
  danger: '#E5484D',
  info: '#2E7CF6',
};

export const darkTheme = {
  name: 'dark',
  colors: {
    background: '#0B0B14',
    surface: '#15151F',
    surfaceAlt: '#1E1E2C',
    surfaceElevated: '#1B1B27',
    text: '#F4F5FA',
    textSecondary: '#A2A8BE',
    textTertiary: '#757B92',
    border: '#272736',
    borderStrong: '#3A3B50',
    primary: '#3B39F5',
    primaryText: '#FFFFFF',
    primarySoft: 'rgba(59, 57, 245, 0.18)',
    onPrimarySoft: '#B9B8FF',
    success: '#29C46F',
    warning: '#F2B357',
    danger: '#FF6B6F',
    info: '#5B9BFF',
    overlay: 'rgba(0, 0, 0, 0.6)',
    shadow: '#000000',
    // Карта (поэтажные планы) — как в макетах папки Map/
    mapRoom: '#2B2F55',
    mapRoomAlt: '#262A4C',
    mapCorridor: '#1B1B29',
    mapHall: '#232741',
    mapStairs: '#33386B',
    mapWall: '#4A5075',
    mapOutline: '#5A6189',
    mapBuilding: '#242946',
    mapPath: '#20233A',
    mapParking: '#1F2338',
    mapPlaza: '#1E2136',
    mapGreen: '#1D3324',
    mapLabel: '#AEB4D4',
    mapLabelStrong: '#EDEFFA',
    mapRoute: '#4E7BFF',
    mapRouteCasing: '#0B0B14',
    mapStart: '#29C46F',
    mapEnd: '#FF6B6F',
    mapUserLocation: '#5B9BFF',
    // Реальный SVG-план светлый, поэтому в тёмной теме он затемняется
    mapOverlay: 'rgba(6, 8, 22, 0.62)',
  },
};
