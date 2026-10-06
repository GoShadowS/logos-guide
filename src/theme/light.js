/**
 * Светлая тема оформления.
 * Фирменный цвет колледжа «Логос» — электрик-синий #1A18E0
 * (взят из дизайн-макетов проекта, папка Design/ репозитория).
 */

/** Отступы (8-пунктная сетка) */
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
  sm: 9,
  md: 14,
  lg: 18,
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
  primary: '#1512E8',
  primaryDark: '#100CB8',
  primaryLight: '#5452F4',
  primarySoft: '#EEF0FF',
  route: '#1A18E0',
  routeSoft: 'rgba(26, 24, 224, 0.12)',
  success: '#0E9F5A',
  warning: '#E8A33D',
  danger: '#E5484D',
  info: '#2E7CF6',
};

export const lightTheme = {
  name: 'light',
  colors: {
    background: '#FFFFFF',
    surface: '#FFFFFF',
    surfaceAlt: '#F2F3FC',
    surfaceElevated: '#FFFFFF',
    text: '#101116',
    textSecondary: '#70727D',
    textTertiary: '#A9ABB4',
    border: '#ECEEF4',
    borderStrong: '#D4D6DF',
    primary: brand.primary,
    primaryText: '#FFFFFF',
    primarySoft: brand.primarySoft,
    onPrimarySoft: brand.primaryDark,
    success: brand.success,
    warning: brand.warning,
    danger: brand.danger,
    info: brand.info,
    overlay: 'rgba(16, 18, 35, 0.45)',
    shadow: '#101223',
    // Карта (поэтажные планы)
    mapRoom: '#E7E9FB',
    mapRoomAlt: '#DFE3FA',
    mapCorridor: '#F3F4FA',
    mapHall: '#EDEFFB',
    mapStairs: '#D6DBFA',
    mapWall: '#AEB4CE',
    mapOutline: '#8E96B5',
    mapBuilding: '#DFE3F8',
    mapPath: '#E9ECF6',
    mapParking: '#E4E8F4',
    mapPlaza: '#EDEFF7',
    mapGreen: '#E3F1E4',
    mapLabel: '#4A5069',
    mapLabelStrong: '#1B1F33',
    mapRoute: brand.route,
    mapRouteCasing: '#FFFFFF',
    mapStart: brand.success,
    mapEnd: brand.danger,
    mapUserLocation: '#2E7CF6',
    // Затемнение реального SVG-плана в тёмной теме (null — не затемнять)
    mapOverlay: null,
  },
};
