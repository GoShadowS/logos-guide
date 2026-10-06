/** A lightweight schedule tab placeholder matching the guide's visual system. */
import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';

export function ScheduleScreen() {
  const { theme } = useTheme();
  const { t } = useI18n();
  const insets = useSafeAreaInsets();

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={[styles.header, { paddingTop: Math.max(insets.top + 12, 24) }]}>
        <Text style={[styles.title, { color: theme.colors.text }]}>{t('schedule.title')}</Text>
      </View>
      <ScrollView contentContainerStyle={styles.content}>
        <View style={[styles.card, { backgroundColor: theme.colors.surfaceAlt }]}>
          <View style={[styles.iconWrap, { backgroundColor: theme.colors.surface }]}>
            <MaterialCommunityIcons name="calendar-clock" size={34} color={theme.colors.primary} />
          </View>
          <Text style={[styles.cardTitle, { color: theme.colors.text }]}>{t('schedule.unavailable')}</Text>
          <Text style={[styles.cardHint, { color: theme.colors.textSecondary }]}>{t('schedule.hint')}</Text>
        </View>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 28, paddingBottom: 14 },
  title: { fontSize: 24, fontWeight: '800', letterSpacing: -0.3 },
  content: { flexGrow: 1, justifyContent: 'center', paddingHorizontal: 28, paddingBottom: 30 },
  card: { alignItems: 'center', borderRadius: 20, paddingHorizontal: 24, paddingVertical: 34 },
  iconWrap: { width: 68, height: 68, borderRadius: 22, alignItems: 'center', justifyContent: 'center', marginBottom: 18 },
  cardTitle: { fontSize: 18, lineHeight: 24, fontWeight: '700', textAlign: 'center' },
  cardHint: { maxWidth: 290, fontSize: 13, lineHeight: 19, textAlign: 'center', marginTop: 8 },
});

export default ScheduleScreen;
