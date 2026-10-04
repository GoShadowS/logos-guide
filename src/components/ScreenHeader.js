/**
 * components/ScreenHeader.js — заголовок экрана с кнопкой «назад».
 */
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';

export function ScreenHeader({ title, subtitle, onBack, right, style }) {
  const { theme } = useTheme();
  const { t } = useI18n();
  const insets = useSafeAreaInsets();

  return (
    <View
      style={[
        styles.container,
        {
          paddingTop: insets.top + 8,
          backgroundColor: theme.colors.surface,
          borderBottomColor: theme.colors.border,
        },
        style,
      ]}
    >
      <View style={styles.row}>
        {onBack ? (
          <Pressable
            onPress={onBack}
            accessibilityRole="button"
            accessibilityLabel={t('ui.back')}
            hitSlop={10}
            style={({ pressed }) => [styles.backButton, pressed && { opacity: 0.6 }]}
          >
            <MaterialCommunityIcons name="chevron-left" size={28} color={theme.colors.text} />
          </Pressable>
        ) : null}
        <View style={styles.titleContainer}>
          <Text numberOfLines={1} style={[styles.title, { color: theme.colors.text }]}>
            {title}
          </Text>
          {subtitle ? (
            <Text numberOfLines={1} style={[styles.subtitle, { color: theme.colors.textSecondary }]}>
              {subtitle}
            </Text>
          ) : null}
        </View>
        {right ? <View style={styles.right}>{right}</View> : null}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingBottom: 12,
    paddingHorizontal: 16,
    borderBottomWidth: 1,
  },
  row: { flexDirection: 'row', alignItems: 'center' },
  backButton: { marginRight: 8, marginLeft: -6 },
  titleContainer: { flex: 1, justifyContent: 'center' },
  title: { fontSize: 19, fontWeight: '700' },
  subtitle: { fontSize: 13, marginTop: 2 },
  right: { marginLeft: 8 },
});

export default ScreenHeader;
