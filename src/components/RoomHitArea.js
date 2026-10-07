/**
 * components/RoomHitArea.js — невидимая зона на плане, соответствующая комнате.
 *
 * Кабинеты не перекрываются декоративными иконками: пользователь нажимает
 * непосредственно на контур помещения. Выбранная комната подсвечивается,
 * а её название дополнительно показывается в карточке карты.
 */
import React from 'react';
import { Pressable, Text, View, StyleSheet } from 'react-native';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';

export function RoomHitArea({ poi, floor, selected = false, onPress, dim = false }) {
  const { theme } = useTheme();
  const { language } = useI18n();
  const bounds = poi.roomBounds;

  if (!bounds || !floor?.width || !floor?.height) return null;

  const title = poi.number || poi.name?.[language] || poi.name?.ru || '';
  const position = {
    left: `${(bounds.x / floor.width) * 100}%`,
    top: `${(bounds.y / floor.height) * 100}%`,
    width: `${(bounds.w / floor.width) * 100}%`,
    height: `${(bounds.h / floor.height) * 100}%`,
  };

  return (
    <Pressable
      testID={`room-hit-area-${poi.id}`}
      onPress={() => onPress?.(poi)}
      accessibilityRole="button"
      accessibilityLabel={title}
      accessibilityState={{ selected }}
      style={[
        styles.hitArea,
        position,
        selected && {
          backgroundColor: theme.colors.primary + '2B',
          borderColor: theme.colors.primary,
          borderWidth: 2,
          opacity: 1,
        },
        !selected && dim && styles.dimmed,
      ]}
    >
      {selected ? (
        <View
          pointerEvents="none"
          style={[
            styles.labelPill,
            { backgroundColor: theme.colors.surface, borderColor: theme.colors.primary },
          ]}
        >
          <Text numberOfLines={1} style={[styles.label, { color: theme.colors.text }]}>
            {title}
          </Text>
        </View>
      ) : null}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  hitArea: {
    position: 'absolute',
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 0,
    borderColor: 'transparent',
    borderRadius: 3,
  },
  dimmed: { opacity: 0.5 },
  labelPill: {
    maxWidth: '95%',
    paddingHorizontal: 6,
    paddingVertical: 3,
    borderRadius: 6,
    borderWidth: 1.5,
  },
  label: { fontSize: 11, fontWeight: '800', textAlign: 'center' },
});

export default RoomHitArea;
