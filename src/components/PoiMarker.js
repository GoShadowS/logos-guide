/**
 * components/PoiMarker.js — маркер точки интереса на карте.
 *
 * Позиционируется в процентах от размера контента, поэтому совпадает
 * с координатами viewBox плана (floors.json / rooms.json).
 */
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { useTheme } from '../theme';
import { getMarkerColor } from '../theme/poiTypes';
import { PoiIcon } from './PoiIcon';
import { useI18n } from '../localization/I18nProvider';

export function PoiMarker({
  poi,
  floor,
  size = 34,
  showLabel = false,
  selected = false,
  onPress,
  isRouteEndpoint = false,
}) {
  const { theme } = useTheme();
  const { language } = useI18n();

  const left = `${(poi.x / floor.width) * 100}%`;
  const top = `${(poi.y / floor.height) * 100}%`;
  const color = getMarkerColor(poi.type, theme.isDark);
  const labelText = poi.number || poi.name?.[language] || poi.name?.ru || '';

  const markerSize = selected ? size * 1.25 : size;

  return (
    <Pressable
      onPress={() => onPress && onPress(poi)}
      accessibilityRole="button"
      accessibilityLabel={labelText}
      hitSlop={{ top: 10, bottom: 10, left: 10, right: 10 }}
      style={[styles.wrapper, { left, top }]}
    >
      <View style={styles.markerColumn}>
        {isRouteEndpoint ? (
          <View
            style={[
              styles.endpointRing,
              { borderColor: color, backgroundColor: theme.colors.surface },
            ]}
          />
        ) : null}
        <PoiIcon type={poi.type} size={markerSize} color={color} />
        {showLabel ? (
          <View
            style={[
              styles.labelPill,
              {
                backgroundColor: theme.colors.surface,
                borderColor: selected ? color : theme.colors.border,
              },
            ]}
          >
            <Text numberOfLines={1} style={[styles.label, { color: theme.colors.text }]}>
              {labelText}
            </Text>
          </View>
        ) : null}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    // Маркер центрируется по своей точке
    marginLeft: -22,
    marginTop: -22,
    width: 44,
    height: 44,
  },
  markerColumn: { alignItems: 'center', justifyContent: 'center' },
  endpointRing: {
    position: 'absolute',
    width: 52,
    height: 52,
    borderRadius: 26,
    borderWidth: 3,
    opacity: 0.55,
  },
  labelPill: {
    marginTop: 3,
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
    borderWidth: 1,
    maxWidth: 96,
  },
  label: { fontSize: 10, fontWeight: '700' },
});

export default PoiMarker;
