/**
 * components/EmptyState.js — «пустое состояние»: иконка, заголовок, подсказка.
 */
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';

export function EmptyState({ icon = 'magnify', title, hint, style, children }) {
  const { theme } = useTheme();
  return (
    <View style={[styles.container, style]}>
      <View
        style={[
          styles.iconCircle,
          { backgroundColor: theme.colors.surfaceAlt, borderRadius: 999 },
        ]}
      >
        <MaterialCommunityIcons name={icon} size={34} color={theme.colors.textTertiary} />
      </View>
      <Text style={[styles.title, { color: theme.colors.text }]}>{title}</Text>
      {hint ? <Text style={[styles.hint, { color: theme.colors.textSecondary }]}>{hint}</Text> : null}
      {children}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 40,
    paddingHorizontal: 32,
  },
  iconCircle: {
    width: 76,
    height: 76,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 16,
  },
  title: {
    fontSize: 17,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 6,
  },
  hint: {
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
  },
});

export default EmptyState;
