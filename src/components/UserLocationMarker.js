/** GPS-точка и круг примерной точности поверх SVG-плана. */
import React from 'react';
import Svg, { Circle } from 'react-native-svg';
import { useTheme } from '../theme';

export function UserLocationMarker({ floor, location }) {
  const { theme } = useTheme();
  if (!floor || !location || location.floorId !== floor.id || !location.withinPlan) return null;

  const color = theme.colors.mapUserLocation;
  const accuracyUnits = location.accuracyUnits ?? 100;
  const radius = Math.max(18, Math.min(accuracyUnits, 220));

  return (
    <Svg
      width="100%"
      height="100%"
      viewBox={`0 0 ${floor.width} ${floor.height}`}
      preserveAspectRatio="none"
      testID="user-location-marker"
      pointerEvents="none"
      accessibilityLabel="GPS location"
      style={{ position: 'absolute', top: 0, left: 0 }}
    >
      <Circle cx={location.x} cy={location.y} r={radius} fill={color} fillOpacity={0.12} />
      <Circle cx={location.x} cy={location.y} r={radius} fill="none" stroke={color} strokeWidth={2} strokeOpacity={0.35} />
      <Circle cx={location.x} cy={location.y} r={12} fill="#FFFFFF" />
      <Circle cx={location.x} cy={location.y} r={8} fill={color} />
    </Svg>
  );
}

export default UserLocationMarker;
