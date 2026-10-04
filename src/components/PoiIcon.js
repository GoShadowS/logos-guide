/**
 * components/PoiIcon.js — иконка типа точки интереса в цветном круге.
 * Используется в маркерах на карте, списках и карточках.
 */
import React from 'react';
import { View, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { POI_TYPES, getMarkerColor } from '../theme/poiTypes';
import { useTheme } from '../theme';

export function PoiIcon({ type, size = 40, color, style, iconSize }) {
  const { theme } = useTheme();
  const meta = POI_TYPES[type] || POI_TYPES.classroom;
  const background = color || getMarkerColor(type, theme.isDark);

  return (
    <View
      style={[
        styles.container,
        {
          width: size,
          height: size,
          borderRadius: size / 2,
          backgroundColor: background,
        },
        style,
      ]}
    >
      <MaterialCommunityIcons
        name={meta.icon}
        size={iconSize || Math.round(size * 0.55)}
        color="#FFFFFF"
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
  },
});

export default PoiIcon;
