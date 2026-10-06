/**
 * Home and search screens for the LOGOS guide.
 * The home screen follows the supplied mobile reference: centered wordmark,
 * clear search field, six large quick actions and a compact recent-search list.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { BrandMark } from '../components/BrandMark';
import { Button } from '../components/Button';
import { EmptyState } from '../components/EmptyState';
import { SearchBar } from '../components/SearchBar';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { useSearchHistory } from '../hooks/useSearchHistory';
import { useSpeechToText } from '../hooks/useSpeechToText';
import { QUICK_CATEGORIES } from '../theme/poiTypes';
import {
  getBuildingOfFloor,
  getDefaultStartPoint,
  getFloor,
  getPoisByTypes,
  normalizeText,
  searchPois,
} from '../data';
import { buildRoute } from '../services/pathfinding';

export function SearchScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t, tp, language } = useI18n();
  const insets = useSafeAreaInsets();
  const { history, add } = useSearchHistory();

  const [query, setQuery] = useState(route?.params?.query || '');
  const [submitted, setSubmitted] = useState(route?.params?.query || '');
  const [category, setCategory] = useState(null);
  const inputRef = useRef(null);

  const voice = useSpeechToText({
    language,
    onResult: setQuery,
    onFinal: (transcript) => {
      setQuery(transcript);
      setSubmitted(transcript);
      setCategory(null);
      add(transcript);
    },
  });

  useEffect(() => {
    if (!voice.unavailableReason) return;
    const messages = {
      unavailable: t('search.voiceUnavailable'),
      denied: t('search.voicePermissionDenied'),
      error: t('search.voiceError'),
    };
    Alert.alert(t('search.voiceInput'), messages[voice.unavailableReason] || t('search.voiceError'));
  }, [voice.unavailableReason, t]);

  useEffect(() => {
    const incoming = route?.params?.query;
    if (!incoming) return;
    setQuery(incoming);
    setSubmitted(incoming);
    navigation.setParams({ query: undefined });
  }, [route?.params?.query, navigation]);

  useEffect(() => {
    if (!route?.params?.fromMap) return;
    const timer = setTimeout(() => inputRef.current?.focus(), 250);
    navigation.setParams({ fromMap: undefined });
    return () => clearTimeout(timer);
  }, [route?.params?.fromMap, navigation]);

  const activeQuery = submitted || query;
  const categoryMeta = category ? QUICK_CATEGORIES.find((item) => item.id === category) : null;

  const results = useMemo(() => {
    if (categoryMeta) {
      return categoryMeta.types ? getPoisByTypes(categoryMeta.types) : [];
    }
    if (!activeQuery.trim()) return [];
    return searchPois(activeQuery, 30);
  }, [activeQuery, categoryMeta]);

  const similarQueries = useMemo(() => {
    if (!activeQuery.trim() || categoryMeta || results.length === 0) return [];
    const target = normalizeText(activeQuery);
    const candidates = [];
    const seen = new Set();
    results.forEach((poi) => {
      (poi.aliases || []).forEach((alias) => {
        const normalized = normalizeText(alias);
        if (!normalized || normalized === target || seen.has(normalized)) return;
        seen.add(normalized);
        candidates.push(alias);
      });
    });
    return candidates.slice(0, 5);
  }, [activeQuery, categoryMeta, results]);

  const showResults = activeQuery.trim().length > 0 || category !== null;

  const handleSubmit = useCallback(() => {
    const value = query.trim();
    if (!value) return;
    setSubmitted(value);
    setCategory(null);
    add(value);
  }, [query, add]);

  const handleChange = useCallback((value) => {
    setQuery(value);
    setSubmitted('');
    setCategory(null);
  }, []);

  const returnHome = useCallback(() => {
    setQuery('');
    setSubmitted('');
    setCategory(null);
    inputRef.current?.blur();
  }, []);

  const openPoi = useCallback((poi) => {
    if (query.trim()) add(query.trim());
    navigation.navigate('PoiDetail', { poiId: poi.id });
  }, [navigation, query, add]);

  const buildRouteTo = useCallback((poi) => {
    const start = getDefaultStartPoint();
    if (!start) {
      Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
      return;
    }
    const routeData = buildRoute(start, { floorId: poi.floorId, x: poi.x, y: poi.y });
    if (!routeData.ok) {
      Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
      return;
    }
    if (query.trim()) add(query.trim());
    navigation.navigate('Navigation', {
      routeData,
      startPoi: { id: 'start', type: 'entrance', floorId: start.floorId, x: start.x, y: start.y },
      endPoi: poi,
    });
  }, [add, navigation, query, t]);

  const selectCategory = useCallback((id) => {
    setCategory((current) => current === id ? null : id);
    setSubmitted('');
  }, []);

  const chooseSuggestion = useCallback((suggestion) => {
    setQuery(suggestion);
    setSubmitted(suggestion);
    setCategory(null);
    add(suggestion);
  }, [add]);

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      {showResults ? (
        <View style={[styles.resultsHeader, { paddingTop: Math.max(insets.top + 8, 14) }]}>
          <Pressable
            onPress={returnHome}
            accessibilityRole="button"
            accessibilityLabel={t('ui.back')}
            hitSlop={8}
            style={({ pressed }) => [styles.backButton, { opacity: pressed ? 0.55 : 1 }]}
          >
            <MaterialCommunityIcons name="chevron-left" size={29} color={theme.colors.primary} />
          </Pressable>
          <SearchBar
            ref={inputRef}
            value={query}
            onChangeText={handleChange}
            onSubmit={handleSubmit}
            onVoicePress={voice.toggle}
            voiceActive={voice.recognizing}
            voiceDisabled={!voice.available}
            placeholder={t('search.placeholder')}
            style={styles.resultsSearchBar}
          />
        </View>
      ) : (
        <View style={[styles.homeHeader, { paddingTop: Math.max(insets.top + 8, 20) }]}>
          <Pressable
            onPress={() => navigation.navigate('SettingsTab')}
            accessibilityRole="button"
            accessibilityLabel={t('tabs.more')}
            hitSlop={10}
            style={styles.menuButton}
          >
            <MaterialCommunityIcons name="menu" size={24} color={theme.colors.primary} />
          </Pressable>
          <BrandMark />
          <View style={styles.headerSpacer} />
        </View>
      )}

      {showResults ? (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.resultsContent}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
        >
          <View style={styles.section}>
            <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
              {category
                ? t('quick.' + category)
                : tp('search.found', results.length)}
            </Text>

            {results.length === 0 ? (
              <EmptyState
                icon="magnify-close"
                title={t('search.noResults')}
                hint={t('search.noResultsHint')}
                style={styles.noResults}
              />
            ) : (
              results.map((poi, index) => {
                const floor = getFloor(poi.floorId);
                const building = getBuildingOfFloor(poi.floorId);
                const title = poi.number || poi.name?.[language] || poi.name?.ru;
                return (
                  <View
                    key={poi.id}
                    style={[
                      styles.resultCard,
                      { backgroundColor: theme.colors.surface, borderColor: theme.colors.border },
                    ]}
                  >
                    <Pressable
                      onPress={() => openPoi(poi)}
                      accessibilityRole="button"
                      accessibilityLabel={title}
                      style={({ pressed }) => [styles.resultRow, { opacity: pressed ? 0.78 : 1 }]}
                    >
                      <View style={styles.resultText}>
                        <Text style={[styles.resultTitle, { color: theme.colors.text }]} numberOfLines={1}>
                          {title}
                        </Text>
                        <Text style={[styles.resultSubtitle, { color: theme.colors.textTertiary }]} numberOfLines={1}>
                          {building?.name?.[language] || building?.name?.ru || ''}
                        </Text>
                        <Text style={[styles.resultSubtitle, { color: theme.colors.textTertiary }]} numberOfLines={1}>
                          {t('ui.floor')} {floor?.level ?? ''} · {t('poiType.' + poi.type)}
                        </Text>
                      </View>
                      <MaterialCommunityIcons name="chevron-right" size={23} color={theme.colors.textTertiary} />
                    </Pressable>
                    {index === 0 && !categoryMeta ? (
                      <Button
                        title={t('ui.buildRoute')}
                        icon="map-marker-path"
                        size="md"
                        fullWidth
                        onPress={() => buildRouteTo(poi)}
                        style={styles.resultRouteButton}
                      />
                    ) : null}
                  </View>
                );
              })
            )}
          </View>

          {similarQueries.length > 0 ? (
            <View style={styles.section}>
              <Text style={[styles.sectionTitle, styles.similarTitle, { color: theme.colors.text }]}>
                {t('search.similarQueries')}
              </Text>
              <View style={[styles.suggestionsCard, { backgroundColor: theme.colors.surface, borderColor: theme.colors.border }]}>
                {similarQueries.map((suggestion, index) => (
                  <Pressable
                    key={`${suggestion}-${index}`}
                    onPress={() => chooseSuggestion(suggestion)}
                    accessibilityRole="button"
                    style={({ pressed }) => [
                      styles.suggestionRow,
                      index < similarQueries.length - 1 && { borderBottomColor: theme.colors.border, borderBottomWidth: StyleSheet.hairlineWidth },
                      { opacity: pressed ? 0.6 : 1 },
                    ]}
                  >
                    <MaterialCommunityIcons name="magnify" size={19} color={theme.colors.text} />
                    <Text style={[styles.suggestionText, { color: theme.colors.text }]} numberOfLines={1}>
                      {suggestion}
                    </Text>
                    <MaterialCommunityIcons name="chevron-right" size={22} color={theme.colors.text} />
                  </Pressable>
                ))}
              </View>
            </View>
          ) : null}
        </ScrollView>
      ) : (
        <ScrollView
          style={styles.scroll}
          contentContainerStyle={styles.homeContent}
          keyboardShouldPersistTaps="handled"
          keyboardDismissMode="on-drag"
        >
          <View style={styles.intro}>
            <Text style={[styles.homeTitle, { color: theme.colors.text }]}>
              {t('home.title')}
            </Text>
            <Text style={[styles.homeSubtitle, { color: theme.colors.textTertiary }]}>
              {t('home.subtitle')}
            </Text>
          </View>

          <SearchBar
            ref={inputRef}
            value={query}
            onChangeText={handleChange}
            onSubmit={handleSubmit}
            onVoicePress={voice.toggle}
            voiceActive={voice.recognizing}
            voiceDisabled={!voice.available}
            placeholder={t('home.searchPlaceholder')}
            style={styles.homeSearchBar}
          />

          <View style={styles.section}>
            <Text style={[styles.sectionTitle, styles.homeSectionTitle, { color: theme.colors.text }]}>
              {t('search.quickSearch')}
            </Text>
            <View style={styles.grid}>
              {QUICK_CATEGORIES.map((item) => (
                <Pressable
                  key={item.id}
                  onPress={() => selectCategory(item.id)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: category === item.id }}
                  style={({ pressed }) => [
                    styles.gridItem,
                    {
                      backgroundColor: theme.colors.surfaceAlt,
                      borderColor: theme.colors.surfaceAlt,
                      opacity: pressed ? 0.78 : 1,
                    },
                  ]}
                >
                  <MaterialCommunityIcons name={item.icon} size={35} color={theme.colors.primary} />
                  <Text style={[styles.gridLabel, { color: theme.colors.text }]} numberOfLines={2}>
                    {t('quick.' + item.id)}
                  </Text>
                </Pressable>
              ))}
            </View>
          </View>

          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <Text style={[styles.sectionTitle, styles.homeSectionTitle, { color: theme.colors.text }]}>
                {t('search.recentSearches')}
              </Text>
            </View>
            {history.length > 0 ? (
              <View style={[styles.historyCard, { backgroundColor: theme.colors.surface, borderColor: theme.colors.border }]}>
                {history.slice(0, 5).map((item, index) => (
                  <Pressable
                    key={`${item}-${index}`}
                    onPress={() => chooseSuggestion(item)}
                    accessibilityRole="button"
                    style={({ pressed }) => [
                      styles.historyRow,
                      index < Math.min(history.length, 5) - 1 && { borderBottomColor: theme.colors.border, borderBottomWidth: StyleSheet.hairlineWidth },
                      { opacity: pressed ? 0.6 : 1 },
                    ]}
                  >
                    <MaterialCommunityIcons name="magnify" size={19} color={theme.colors.primary} />
                    <View style={styles.historyTextWrap}>
                      <Text style={[styles.historyText, { color: theme.colors.text }]} numberOfLines={1}>
                        {item}
                      </Text>
                      <Text style={[styles.historyMeta, { color: theme.colors.textTertiary }]} numberOfLines={1}>
                        {t('home.collegeGuide')}
                      </Text>
                    </View>
                    <MaterialCommunityIcons name="chevron-right" size={22} color={theme.colors.text} />
                  </Pressable>
                ))}
              </View>
            ) : (
              <View style={[styles.emptyHistory, { backgroundColor: theme.colors.surfaceAlt }]}>
                <MaterialCommunityIcons name="history" size={19} color={theme.colors.textTertiary} />
                <Text style={[styles.emptyHistoryText, { color: theme.colors.textSecondary }]}>
                  {t('home.noRecentSearches')}
                </Text>
              </View>
            )}
          </View>
        </ScrollView>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  homeHeader: {
    minHeight: 58,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 28,
    paddingBottom: 6,
  },
  menuButton: { width: 32, height: 36, alignItems: 'flex-start', justifyContent: 'center' },
  headerSpacer: { width: 32 },
  resultsHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 24,
    paddingBottom: 9,
  },
  backButton: { width: 38, height: 48, alignItems: 'flex-start', justifyContent: 'center', marginRight: 7 },
  resultsSearchBar: { flex: 1 },
  scroll: { flex: 1 },
  homeContent: { paddingHorizontal: 28, paddingTop: 16, paddingBottom: 30 },
  resultsContent: { paddingHorizontal: 28, paddingTop: 18, paddingBottom: 30 },
  intro: { marginBottom: 16 },
  homeTitle: { fontSize: 22, lineHeight: 28, fontWeight: '800', letterSpacing: -0.3 },
  homeSubtitle: { maxWidth: 330, fontSize: 12.5, lineHeight: 16, marginTop: 3 },
  homeSearchBar: { height: 48, marginBottom: 29 },
  section: { marginBottom: 22 },
  sectionHeader: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 },
  sectionTitle: { fontSize: 20, fontWeight: '800', letterSpacing: -0.2 },
  homeSectionTitle: { fontSize: 20, marginBottom: 12 },
  grid: { flexDirection: 'row', flexWrap: 'wrap', justifyContent: 'space-between', rowGap: 9 },
  gridItem: {
    width: '31.8%',
    aspectRatio: 1,
    borderRadius: 15,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 5,
  },
  gridLabel: { fontSize: 11.5, lineHeight: 14, fontWeight: '600', marginTop: 9, textAlign: 'center' },
  resultCard: {
    borderWidth: 1,
    borderRadius: 15,
    marginTop: 9,
    overflow: 'hidden',
    shadowColor: '#16162A',
    shadowOpacity: 0.035,
    shadowRadius: 7,
    shadowOffset: { width: 0, height: 2 },
    elevation: 1,
  },
  resultRow: { minHeight: 91, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 18, paddingVertical: 14 },
  resultText: { flex: 1, paddingRight: 12 },
  resultTitle: { fontSize: 25, lineHeight: 30, fontWeight: '800', letterSpacing: -0.3 },
  resultSubtitle: { fontSize: 12.5, lineHeight: 15, marginTop: 1 },
  resultRouteButton: { marginHorizontal: 16, marginBottom: 12, minHeight: 43 },
  noResults: { paddingVertical: 36 },
  similarTitle: { marginBottom: 12 },
  suggestionsCard: { borderWidth: 1, borderRadius: 13, overflow: 'hidden' },
  suggestionRow: { minHeight: 51, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 13 },
  suggestionText: { flex: 1, fontSize: 13, fontWeight: '600', marginLeft: 12, marginRight: 10 },
  historyCard: { borderWidth: 1, borderRadius: 13, overflow: 'hidden' },
  historyRow: { minHeight: 50, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 13 },
  historyTextWrap: { flex: 1, marginHorizontal: 12 },
  historyText: { fontSize: 13, lineHeight: 17, fontWeight: '600' },
  historyMeta: { fontSize: 10.5, lineHeight: 13, marginTop: 1 },
  emptyHistory: { minHeight: 58, borderRadius: 14, paddingHorizontal: 15, flexDirection: 'row', alignItems: 'center' },
  emptyHistoryText: { flex: 1, fontSize: 12.5, marginLeft: 10 },
});

export default SearchScreen;
