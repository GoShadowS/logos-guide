/**
 * components/RouteStepItem.js — один шаг маршрута в списке инструкций.
 */
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { roundMeters } from '../utils/format';

/** Иконка шага по его типу */
function stepIcon(kind) {
  switch (kind) {
    case 'left':
      return 'arrow-left-bold';
    case 'right':
      return 'arrow-right-bold';
    case 'slightLeft':
      return 'arrow-top-left-bold-outline';
    case 'slightRight':
      return 'arrow-top-right-bold-outline';
    case 'around':
      return 'rotate-left';
    case 'stairsUp':
      return 'stairs-up';
    case 'stairsDown':
      return 'stairs-down';
    case 'elevatorUp':
      return 'elevator-up';
    case 'elevatorDown':
      return 'elevator-down';
    case 'arrive':
      return 'flag-checkered';
    default:
      return 'arrow-up-bold';
  }
}

/** Текст инструкции шага */
export function stepText(step, t) {
  switch (step.kind) {
    case 'left':
      return t('ui.turnLeft');
    case 'right':
      return t('ui.turnRight');
    case 'slightLeft':
      return t('ui.slightLeft');
    case 'slightRight':
      return t('ui.slightRight');
    case 'around':
      return t('ui.turnAround');
    case 'stairsUp':
      return t('ui.goUpStairs', { floor: step.level });
    case 'stairsDown':
      return t('ui.goDownStairs', { floor: step.level });
    case 'elevatorUp':
      return t('ui.goUpElevator', { floor: step.level });
    case 'elevatorDown':
      return t('ui.goDownElevator', { floor: step.level });
    case 'arrive':
      return t('ui.arrive');
    default:
      return t('ui.goStraight');
  }
}

export function RouteStepItem({ step, index, active, compact }) {
  const { theme } = useTheme();
  const { t } = useI18n();

  const icon = stepIcon(step.kind);
  const text = stepText(step, t);
  const distance = step.distanceM > 0.5 ? `${roundMeters(step.distanceM)} ${t('ui.m')}` : null;

  return (
    <View
      style={[
        styles.container,
        {
          backgroundColor: active ? theme.colors.primarySoft : 'transparent',
          borderRadius: theme.radius.md,
          paddingVertical: compact ? 8 : 11,
        },
      ]}
    >
      <View
        style={[
          styles.numberCircle,
          {
            backgroundColor: active ? theme.colors.primary : theme.colors.surfaceAlt,
          },
        ]}
      >
        {active ? (
          <MaterialCommunityIcons name={icon} size={17} color={theme.colors.primaryText} />
        ) : (
          <Text style={[styles.number, { color: theme.colors.textSecondary }]}>{index + 1}</Text>
        )}
      </View>
      <View style={styles.textWrap}>
        <Text
          style={[
            styles.text,
            { color: active ? theme.colors.primary : theme.colors.text, fontWeight: active ? '700' : '500' },
          ]}
        >
          {text}
        </Text>
        {distance ? (
          <Text style={[styles.distance, { color: theme.colors.textSecondary }]}>{distance}</Text>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
  },
  numberCircle: {
    width: 30,
    height: 30,
    borderRadius: 15,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  number: { fontSize: 13, fontWeight: '700' },
  textWrap: { flex: 1 },
  text: { fontSize: 14.5, lineHeight: 19 },
  distance: { fontSize: 12, marginTop: 1 },
});

export default RouteStepItem;
