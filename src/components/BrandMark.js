/**
 * Brand wordmark used by the home screen and loading surfaces.
 * The compact text treatment keeps the LOGOS identity crisp at phone sizes.
 */
import React from 'react';
import { View, Text, StyleSheet } from 'react-native';
import { useI18n } from '../localization/I18nProvider';
import { useTheme } from '../theme';

export function BrandMark({ style, compact = false }) {
  const { theme } = useTheme();
  const { language } = useI18n();

  return (
    <View style={[styles.container, compact && styles.compact, style]} accessibilityRole="image">
      <Text style={[styles.name, compact && styles.compactName, { color: theme.colors.primary }]}>
        {language === 'ru' ? 'ЛОГОС' : 'LOGOS'}
      </Text>
      <Text style={[styles.tagline, compact && styles.compactTagline, { color: theme.colors.text }]}>
        {language === 'ru' ? 'П У Т Е В О Д И Т Е Л Ь' : 'G U I D E B O O K'}
      </Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { alignItems: 'center', justifyContent: 'center' },
  compact: { transform: [{ scale: 0.92 }] },
  name: {
    fontSize: 25,
    lineHeight: 28,
    fontWeight: '900',
    letterSpacing: -1.5,
  },
  compactName: { fontSize: 19, lineHeight: 22 },
  tagline: {
    marginTop: 0,
    fontSize: 7.5,
    lineHeight: 10,
    fontWeight: '700',
    letterSpacing: 1.3,
  },
  compactTagline: { fontSize: 6.5, lineHeight: 8 },
});

export default BrandMark;
