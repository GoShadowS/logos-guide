/**
 * components/RouteOverlay.js — отрисовка построенного маршрута поверх плана.
 *
 * Линия маршрута рисуется в той же системе координат, что и план (viewBox),
 * поэтому точно ложится на коридоры. Концы маршрута обозначаются кругами.
 */
import React, { useMemo } from 'react';
import { StyleSheet } from 'react-native';
import Svg, { Polyline, Circle, G } from 'react-native-svg';
import { useTheme } from '../theme';

export function RouteOverlay({
  floor,
  points = [],
  theme,
  startColor,
  endColor,
  progress = 1,
}) {
  const colors = theme.colors;

  // Оставляем только точки текущего этажа
  const floorPoints = useMemo(
    () => points.filter((p) => p.floorId === floor.id),
    [points, floor.id]
  );

  if (floorPoints.length === 0) return null;

  const pointsString = floorPoints.map((p) => `${p.x},${p.y}`).join(' ');
  const start = floorPoints[0];
  const end = floorPoints[floorPoints.length - 1];

  return (
    <Svg
      width="100%"
      height="100%"
      viewBox={`0 0 ${floor.width} ${floor.height}`}
      preserveAspectRatio="none"
      style={styles.svg}
      pointerEvents="none"
    >
      {/* «Обводка» линии (светлая кайма) */}
      <Polyline
        points={pointsString}
        fill="none"
        stroke={colors.mapRouteCasing}
        strokeWidth={16}
        strokeLinecap="round"
        strokeLinejoin="round"
        opacity={0.9}
      />
      {/* Основная линия маршрута */}
      <Polyline
        points={pointsString}
        fill="none"
        stroke={colors.mapRoute}
        strokeWidth={9}
        strokeLinecap="round"
        strokeLinejoin="round"
      />
      {/* Точка старта */}
      <G>
        <Circle cx={start.x} cy={start.y} r={13} fill={startColor || colors.mapStart} />
        <Circle cx={start.x} cy={start.y} r={5.5} fill="#FFFFFF" />
      </G>
      {/* Точка финиша */}
      <G>
        <Circle cx={end.x} cy={end.y} r={15} fill={endColor || colors.mapEnd} />
        <Circle cx={end.x} cy={end.y} r={6} fill="#FFFFFF" />
      </G>
    </Svg>
  );
}

const styles = StyleSheet.create({
  svg: { position: 'absolute', top: 0, left: 0 },
});

export default RouteOverlay;
