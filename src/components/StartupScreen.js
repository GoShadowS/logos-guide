/** Branded loading surface matching the supplied LOGOS splash reference. */
import React from 'react';
import { ImageBackground, View, Text, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useI18n } from '../localization/I18nProvider';

export function StartupScreen() {
  const { language, t } = useI18n();
  const insets = useSafeAreaInsets();

  return (
    <ImageBackground
      source={require('../../assets/images/college-exterior.jpg')}
      resizeMode="cover"
      style={styles.container}
    >
      <View style={styles.wash} />

      <View style={styles.wordmark} accessibilityRole="image">
        <Text style={styles.logoText}>{language === 'ru' ? 'ЛОГОС' : 'LOGOS'}</Text>
        <Text style={styles.tagline}>
          {language === 'ru' ? 'П У Т Е В О Д И Т Е Л Ь' : 'G U I D E B O O K'}
        </Text>
      </View>

      <View style={[styles.loading, { bottom: Math.max(insets.bottom + 27, 38) }]}>
        <Text style={styles.loadingTitle}>{t('splash.loading')}</Text>
        <Text style={styles.loadingHint}>{t('splash.searching')}</Text>
        <Text style={styles.loadingSubhint}>{t('splash.entering')}</Text>
        <View style={styles.track}>
          <View style={styles.progress} />
        </View>
      </View>
    </ImageBackground>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: '#FFFFFF' },
  wash: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(255,255,255,0.82)' },
  wordmark: { position: 'absolute', top: '45%', left: 0, right: 0, alignItems: 'center' },
  logoText: { color: '#1512E8', fontSize: 42, lineHeight: 48, fontWeight: '900', letterSpacing: -2 },
  tagline: { color: '#25254F', fontSize: 10, lineHeight: 15, fontWeight: '700', letterSpacing: 2.3, marginTop: 2 },
  loading: { position: 'absolute', left: 18, right: 18, alignItems: 'center' },
  loadingTitle: { color: '#111116', fontSize: 16, lineHeight: 22, fontWeight: '800' },
  loadingHint: { color: '#A7A7B0', fontSize: 13, lineHeight: 18, fontWeight: '600', marginTop: 6 },
  loadingSubhint: { color: '#C3C3C9', fontSize: 11, lineHeight: 16, fontWeight: '500', marginTop: 3, marginBottom: 10 },
  track: { width: 116, height: 6, borderRadius: 3, backgroundColor: 'rgba(255,255,255,0.92)', overflow: 'hidden' },
  progress: { width: '46%', height: '100%', borderRadius: 3, backgroundColor: '#1512E8' },
});

export default StartupScreen;
