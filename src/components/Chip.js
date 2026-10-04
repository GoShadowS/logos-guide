/**
 * components/Chip.js — чипсы-фильтры и переключатели.
 */
import React from 'react';
import { Pressable, Text, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';

export function Chip({ label, selected, onPress, icon, style, textStyle }) {
  const { theme } = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityState={{ selected: !!selected }}
      style={({ pressed }) => [
        styles.chip,
        {
          backgroundColor: selected ? theme.colors.primary : theme.colors.surface,
          borderColor: selected ? theme.colors.primary : theme.colors.border,
          borderRadius: theme.radius.pill,
          opacity: pressed ? 0.85 : 1,
        },
        style,
      ]}
    >
      {icon ? (
        <MaterialCommunityIcons
          name={icon}
          size={16}
          color={selected ? theme.colors.primaryText : theme.colors.textSecondary}
          style={styles.icon}
        />
      ) : null}
      <Text
        numberOfLines={1}
        style={[
          styles.label,
          { color: selected ? theme.colors.primaryText : theme.colors.textSecondary },
          textStyle,
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 7,
    paddingHorizontal: 13,
    borderWidth: 1,
  },
  icon: { marginRight: 6 },
  label: { fontSize: 13, fontWeight: '600' },
});

export default Chip;
