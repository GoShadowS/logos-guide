/**
 * components/Card.js — карточка-контейнер (поверхность с тенью и скруглением).
 */
import React from 'react';
import { View, StyleSheet, Pressable } from 'react-native';
import { useTheme } from '../theme';

export function Card({ children, style, onPress, padded = true, elevation = 'sm' }) {
  const { theme } = useTheme();
  const shadow =
    elevation === 'none'
      ? {}
      : {
          shadowColor: theme.colors.shadow,
          shadowOpacity: theme.isDark ? 0.4 : 0.06,
          shadowRadius: elevation === 'lg' ? 18 : 8,
          shadowOffset: { width: 0, height: elevation === 'lg' ? 8 : 3 },
          elevation: elevation === 'lg' ? 6 : 2,
        };

  const base = [
    styles.card,
    {
      backgroundColor: theme.colors.surface,
      borderRadius: theme.radius.lg,
      borderColor: theme.colors.border,
      borderWidth: theme.isDark ? 1 : 0,
    },
    padded && { padding: theme.spacing.lg },
    shadow,
    style,
  ];

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        style={({ pressed }) => [...base, pressed && { opacity: 0.9 }]}
      >
        {children}
      </Pressable>
    );
  }
  return <View style={base}>{children}</View>;
}

const styles = StyleSheet.create({
  card: {
    overflow: 'hidden',
  },
});

export default Card;
