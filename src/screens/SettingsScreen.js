/**
 * screens/SettingsScreen.js — настройки приложения:
 * язык, тема, режим карты, история поиска, о приложении.
 */
import React, { useCallback, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import Constants from 'expo-constants';

import { Card } from '../components/Card';
import { SettingRow } from '../components/SettingRow';
import { LanguageSwitcher } from '../components/LanguageSwitcher';
import { SegmentedControl } from '../components/SegmentedControl';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { useSettings } from '../hooks/useSettings';
import { useSearchHistory } from '../hooks/useSearchHistory';
import { config } from '../data';

export function SettingsScreen({ navigation }) {
  const { theme, preference: themePreference, setPreference } = useTheme();
  const { t, language, preference, setLanguage } = useI18n();
  const insets = useSafeAreaInsets();
  const { reset } = useSettings();
  const { clear } = useSearchHistory();
  const appVersion = Constants.expoConfig?.version || config.college.appVersion;

  const handleClearHistory = useCallback(async () => {
    await clear();
    Alert.alert(t('settings.clearHistory'), t('settings.clearHistoryDone'));
  }, [clear, t]);

  const handleReset = useCallback(async () => {
    const defaults = await reset();
    await Promise.all([setPreference(defaults.theme), setLanguage(defaults.language)]);
    Alert.alert(t('settings.reset'), t('settings.resetDone'));
  }, [reset, setPreference, setLanguage, t]);

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <Text style={[styles.title, { color: theme.colors.text }]}>{t('tabs.more')}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        {/* Язык */}
        <Card style={styles.card}>
          <SettingRow icon="translate" title={t('settings.language')} />
          <View style={styles.switcherWrap}>
            <LanguageSwitcher value={preference} onChange={setLanguage} />
          </View>
        </Card>

        {/* Тема */}
        <Card style={styles.card}>
          <SettingRow icon="theme-light-dark" title={t('settings.theme')} />
          <View style={styles.switcherWrap}>
            <SegmentedControl
              value={themePreference}
              onChange={setPreference}
              options={[
                { value: 'system', label: t('settings.themeSystem'), icon: 'theme-light-dark' },
                { value: 'light', label: t('settings.light'), icon: 'white-balance-sunny' },
                { value: 'dark', label: t('settings.dark'), icon: 'moon-waning-crescent' },
              ]}
            />
          </View>
        </Card>

        {/* Данные и хранилище */}
        <Card style={styles.card}>
          <SettingRow
            icon="heart-outline"
            title={t('favorites.title')}
            onPress={() => navigation.navigate('FavoritesTab')}
          />
          <SettingRow
            icon="delete-outline"
            title={t('settings.clearHistory')}
            onPress={handleClearHistory}
          />
        </Card>

        {/* О приложении */}
        <Card style={styles.card}>
          <SettingRow
            icon="information-outline"
            title={t('settings.about')}
            value={appVersion}
            onPress={() => navigation.navigate('About')}
          />
          <SettingRow icon="restore" title={t('settings.reset')} onPress={handleReset} danger />
        </Card>

        <Text style={[styles.footer, { color: theme.colors.textTertiary }]}>
          {config.college.appName?.[language] || config.college.appName?.ru} · v{appVersion}
        </Text>
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 16, paddingBottom: 8 },
  title: { fontSize: 26, fontWeight: '800' },
  scroll: { padding: 12, paddingBottom: 40 },
  card: { marginBottom: 12 },
  switcherWrap: { paddingHorizontal: 16, paddingBottom: 12 },
  footer: { fontSize: 12, textAlign: 'center', marginTop: 8 },
});

export default SettingsScreen;
