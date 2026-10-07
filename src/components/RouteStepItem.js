/** One numbered navigation instruction with a directional glyph. */
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { roundMeters } from '../utils/format';

function stepIcon(kind) {
  switch (kind) {
    case 'left': return 'arrow-left-bold';
    case 'right': return 'arrow-right-bold';
    case 'slightLeft': return 'arrow-top-left-bold-outline';
    case 'slightRight': return 'arrow-top-right-bold-outline';
    case 'around': return 'rotate-left';
    case 'stairsUp': return 'stairs-up';
    case 'stairsDown': return 'stairs-down';
    case 'elevatorUp': return 'elevator-up';
    case 'elevatorDown': return 'elevator-down';
    case 'arrive': return 'flag-checkered';
    default: return 'arrow-up-bold';
  }
}

export function stepText(step, t) {
  switch (step.kind) {
    case 'left': return t('ui.turnLeft');
    case 'right': return t('ui.turnRight');
    case 'slightLeft': return t('ui.slightLeft');
    case 'slightRight': return t('ui.slightRight');
    case 'around': return t('ui.turnAround');
    case 'stairsUp': return t('ui.goUpStairs', { floor: step.level });
    case 'stairsDown': return t('ui.goDownStairs', { floor: step.level });
    case 'elevatorUp': return t('ui.goUpElevator', { floor: step.level });
    case 'elevatorDown': return t('ui.goDownElevator', { floor: step.level });
    case 'arrive': return t('ui.arrive');
    default: return t('ui.goStraight');
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
        compact && styles.compact,
        {
          borderBottomColor: theme.colors.border,
          backgroundColor: active ? theme.colors.primarySoft : 'transparent',
        },
      ]}
    >
      <View
        style={[
          styles.numberCircle,
          compact && styles.compactCircle,
          { backgroundColor: active ? theme.colors.primary : theme.colors.primarySoft },
        ]}
      >
        <Text style={[styles.number, { color: active ? theme.colors.primaryText : theme.colors.primary }]}>
          {index + 1}
        </Text>
      </View>
      <MaterialCommunityIcons
        name={icon}
        size={compact ? 22 : 25}
        color={theme.colors.primary}
        style={styles.directionIcon}
      />
      <View style={styles.textWrap}>
        <Text style={[styles.text, { color: active ? theme.colors.primary : theme.colors.text }]}>
          {text}
        </Text>
        {distance ? (
          <Text style={[styles.distance, { color: theme.colors.textTertiary }]}>{distance}</Text>
        ) : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    minHeight: 66,
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 4,
    paddingVertical: 8,
    borderBottomWidth: StyleSheet.hairlineWidth,
  },
  compact: { minHeight: 52, paddingVertical: 6 },
  numberCircle: {
    width: 43,
    height: 43,
    borderRadius: 22,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 13,
  },
  compactCircle: { width: 34, height: 34, borderRadius: 17, marginRight: 10 },
  number: { fontSize: 20, lineHeight: 24, fontWeight: '800' },
  directionIcon: { marginRight: 12 },
  textWrap: { flex: 1 },
  text: { fontSize: 15, lineHeight: 19, fontWeight: '600' },
  distance: { fontSize: 12.5, lineHeight: 16, marginTop: 1 },
});

export default RouteStepItem;
