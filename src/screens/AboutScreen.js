/**
 * screens/AboutScreen.js — информация о приложении.
 */
import React from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import Constants from 'expo-constants';
import * as WebBrowser from 'expo-web-browser';

import { ScreenHeader } from '../components/ScreenHeader';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { config } from '../data';

export function AboutScreen({ navigation }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const appVersion = Constants.expoConfig?.version || config.college.appVersion;

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <ScreenHeader title={t('about.title')} onBack={() => navigation.goBack()} />

      <ScrollView contentContainerStyle={styles.scroll}>
        <Card style={styles.card}>
          <Text style={[styles.appName, { color: theme.colors.text }]}>
            {config.college.appName?.[language] || config.college.appName?.ru}
          </Text>
          <Text style={[styles.description, { color: theme.colors.textSecondary }]}>
            {t('about.description')}
          </Text>
        </Card>

        <Card style={styles.card}>
          <Row label={t('about.college')} value={t('about.collegeName')} />
          <Row label={t('about.developer')} value={t('about.developerName')} />
          <Row label={t('about.dataSource')} value={t('about.dataSourceName')} />
          <Row label={t('about.license')} value={t('about.licenseName')} />
          <Row label={t('settings.version')} value={appVersion} />
        </Card>

        <Button
          title="GitHub: GoShadowS/LOGOS-Guidebook"
          icon="open-in-new"
          variant="outline"
          fullWidth
          onPress={() => WebBrowser.openBrowserAsync('https://github.com/GoShadowS/LOGOS-Guidebook')}
        />
      </ScrollView>
    </View>
  );
}

function Row({ label, value }) {
  const { theme } = useTheme();
  return (
    <View style={styles.row}>
      <Text style={[styles.rowLabel, { color: theme.colors.textSecondary }]}>{label}</Text>
      <Text style={[styles.rowValue, { color: theme.colors.text }]}>{value}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll: { padding: 12, paddingBottom: 40 },
  card: { marginBottom: 12 },
  appName: { fontSize: 22, fontWeight: '800', marginBottom: 8 },
  description: { fontSize: 14.5, lineHeight: 21 },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 7,
  },
  rowLabel: { fontSize: 13.5 },
  rowValue: { fontSize: 14, fontWeight: '600', flexShrink: 1, textAlign: 'right', marginLeft: 12 },
});

export default AboutScreen;
