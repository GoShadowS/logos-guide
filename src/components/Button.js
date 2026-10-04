/**
 * components/Button.js — кнопки приложения.
 * variant: 'primary' | 'secondary' | 'outline' | 'ghost'
 */
import React from 'react';
import { Pressable, Text, StyleSheet, ActivityIndicator, View } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';

export function Button({
  title,
  onPress,
  variant = 'primary',
  size = 'md',
  icon,
  disabled,
  loading,
  style,
  textStyle,
  fullWidth,
}) {
  const { theme } = useTheme();

  const palette = {
    primary: { bg: theme.colors.primary, fg: theme.colors.primaryText, border: 'transparent' },
    secondary: { bg: theme.colors.surfaceAlt, fg: theme.colors.text, border: 'transparent' },
    outline: { bg: 'transparent', fg: theme.colors.primary, border: theme.colors.primary },
    ghost: { bg: 'transparent', fg: theme.colors.textSecondary, border: 'transparent' },
  }[variant];

  const sizes = {
    sm: { paddingVertical: 8, paddingHorizontal: 12, fontSize: 13, iconSize: 16, radius: theme.radius.sm },
    md: { paddingVertical: 13, paddingHorizontal: 18, fontSize: 15, iconSize: 18, radius: theme.radius.md },
    lg: { paddingVertical: 16, paddingHorizontal: 22, fontSize: 16, iconSize: 20, radius: theme.radius.lg },
  }[size];

  return (
    <Pressable
      onPress={onPress}
      disabled={disabled || loading}
      accessibilityRole="button"
      accessibilityLabel={title}
      style={({ pressed }) => [
        styles.base,
        {
          backgroundColor: palette.bg,
          borderColor: palette.border,
          borderRadius: sizes.radius,
          paddingVertical: sizes.paddingVertical,
          paddingHorizontal: sizes.paddingHorizontal,
          opacity: disabled ? 0.5 : pressed ? 0.85 : 1,
        },
        fullWidth && styles.fullWidth,
        style,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={palette.fg} size="small" />
      ) : (
        <View style={styles.content}>
          {icon ? (
            <MaterialCommunityIcons
              name={icon}
              size={sizes.iconSize}
              color={palette.fg}
              style={styles.icon}
            />
          ) : null}
          <Text style={[styles.text, { color: palette.fg, fontSize: sizes.fontSize }, textStyle]} numberOfLines={1}>
            {title}
          </Text>
        </View>
      )}
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  fullWidth: { width: '100%' },
  content: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center' },
  icon: { marginRight: 8 },
  text: { fontWeight: '600', textAlign: 'center' },
});

export default Button;
