/**
 * components/SegmentedControl.js — переключатель вариантов (сегменты).
 * Универсальный: используется для языка, темы и режима карты.
 */
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';

export function SegmentedControl({ options = [], value, onChange, style }) {
  const { theme } = useTheme();

  return (
    <View style={[styles.row, style]}>
      {options.map((option) => {
        const active = option.value === value;
        return (
          <Pressable
            key={option.value}
            onPress={() => onChange(option.value)}
            accessibilityRole="button"
            accessibilityState={{ selected: active }}
            style={({ pressed }) => [
              styles.segment,
              {
                backgroundColor: active ? theme.colors.primary : theme.colors.surfaceAlt,
                borderColor: active ? theme.colors.primary : theme.colors.border,
                opacity: pressed ? 0.9 : 1,
              },
            ]}
          >
            {option.icon ? (
              <MaterialCommunityIcons
                name={option.icon}
                size={16}
                color={active ? theme.colors.primaryText : theme.colors.textSecondary}
                style={styles.icon}
              />
            ) : null}
            <Text
              numberOfLines={1}
              style={[
                styles.label,
                { color: active ? theme.colors.primaryText : theme.colors.textSecondary },
              ]}
            >
              {option.label}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  row: { flexDirection: 'row', flexWrap: 'wrap' },
  segment: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 999,
    borderWidth: 1,
    marginRight: 8,
    marginBottom: 8,
  },
  icon: { marginRight: 6 },
  label: { fontSize: 13.5, fontWeight: '600' },
});

export default SegmentedControl;
