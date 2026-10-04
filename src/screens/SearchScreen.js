/**
 * screens/SearchScreen.js — поиск аудиторий, мест и преподавателей.
 *
 * Возможности:
 *   • поиск по номеру аудитории, названию, преподавателю и предмету;
 *   • быстрые категории (кабинеты, преподаватели, туалеты, еда…);
 *   • история поиска;
 *   • голосовой ввод запроса.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { SearchBar } from '../components/SearchBar';
import { Card } from '../components/Card';
import { EmptyState } from '../components/EmptyState';
import { PoiIcon } from '../components/PoiIcon';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { useSearchHistory } from '../hooks/useSearchHistory';
import { useSpeechToText } from '../hooks/useSpeechToText';
import { QUICK_CATEGORIES } from '../theme/poiTypes';
import {
  searchPois,
  getPoisByTypes,
  teachers,
  getPoiOfTeacher,
  getTeacher,
  getFloor,
  getBuildingOfFloor,
} from '../data';
import { formatFloorAndBuilding } from '../utils/format';

export function SearchScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t, tp, language } = useI18n();
  const insets = useSafeAreaInsets();
  const { history, add, remove, clear } = useSearchHistory();

  const [query, setQuery] = useState(route?.params?.query || '');
  const [submitted, setSubmitted] = useState(route?.params?.query || '');
  const [category, setCategory] = useState(null); // активная категория быстрого поиска
  const inputRef = useRef(null);

  // --- Голосовой ввод ------------------------------------------------------
  const voice = useSpeechToText({
    language,
    onResult: (transcript) => setQuery(transcript),
    onFinal: (transcript) => {
      setQuery(transcript);
      setSubmitted(transcript);
      setCategory(null);
      add(transcript);
    },
  });

  // Сообщаем, почему голосовой ввод не запустился
  useEffect(() => {
    if (!voice.unavailableReason) return;
    const messages = {
      unavailable: t('search.voiceUnavailable'),
      denied: t('search.voicePermissionDenied'),
      error: t('search.voiceError'),
    };
    Alert.alert(t('search.voiceInput'), messages[voice.unavailableReason] || t('search.voiceError'));
  }, [voice.unavailableReason, t]);

  // --- Запрос, переданный с другого экрана --------------------------------
  useEffect(() => {
    const incoming = route?.params?.query;
    if (incoming) {
      setQuery(incoming);
      setSubmitted(incoming);
      navigation.setParams({ query: undefined });
    }
  }, [route?.params?.query, navigation]);

  // Если пришли с карты — сразу ставим курсор в поле ввода
  useEffect(() => {
    if (!route?.params?.fromMap) return;
    const timer = setTimeout(() => inputRef.current?.focus(), 250);
    navigation.setParams({ fromMap: undefined });
    return () => clearTimeout(timer);
  }, [route?.params?.fromMap, navigation]);

  // --- Результаты ----------------------------------------------------------
  const activeQuery = submitted || query;
  const categoryMeta = category ? QUICK_CATEGORIES.find((item) => item.id === category) : null;

  const results = useMemo(() => {
    if (categoryMeta) {
      if (categoryMeta.teachers) return []; // преподаватели выводятся отдельным списком
      if (categoryMeta.types) return getPoisByTypes(categoryMeta.types);
      return [];
    }
    if (activeQuery.trim().length === 0) return [];
    return searchPois(activeQuery, 30);
  }, [activeQuery, categoryMeta]);

  const teachersList = useMemo(
    () =>
      teachers
        .map((teacher) => ({ teacher, poi: getPoiOfTeacher(teacher.id) }))
        .filter((item) => item.poi),
    []
  );

  const showResults = activeQuery.trim().length > 0 || category !== null;

  const handleSubmit = useCallback(() => {
    const value = query.trim();
    if (!value) return;
    setSubmitted(value);
    setCategory(null);
    add(value);
  }, [query, add]);

  const handleClearInput = useCallback((value) => {
    setQuery(value);
    setSubmitted('');
    setCategory(null);
  }, []);

  const openPoi = useCallback(
    (poi) => {
      const value = query.trim();
      if (value) add(value);
      navigation.navigate('PoiDetail', { poiId: poi.id });
    },
    [navigation, query, add]
  );

  const selectCategory = useCallback(
    (id) => {
      setCategory((current) => (current === id ? null : id));
      setSubmitted('');
    },
    []
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={[styles.header, { paddingTop: insets.top + 10 }]}>
        <SearchBar
          ref={inputRef}
          value={query}
          onChangeText={handleClearInput}
          onSubmit={handleSubmit}
          onVoicePress={voice.toggle}
          voiceActive={voice.recognizing}
          voiceDisabled={!voice.available}
          placeholder={t('search.placeholder')}
          style={styles.searchBar}
        />
      </View>

      <ScrollView
        style={styles.scroll}
        contentContainerStyle={styles.scrollContent}
        keyboardShouldPersistTaps="handled"
        keyboardDismissMode="on-drag"
      >
        {showResults ? (
          <View style={styles.section}>
            <View style={styles.sectionHeader}>
              <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
                {category
                  ? t('quick.' + category)
                  : tp('search.found', results.length)}
              </Text>
              {category ? (
                <Pressable onPress={() => setCategory(null)} hitSlop={8} accessibilityRole="button">
                  <Text style={[styles.clearText, { color: theme.colors.primary }]}>
                    {t('ui.clear')}
                  </Text>
                </Pressable>
              ) : null}
            </View>

            {category === 'teachers' ? (
              teachersList.length === 0 ? (
                <EmptyState
                  icon="account-search-outline"
                  title={t('search.noResults')}
                  hint={t('search.noResultsHint')}
                />
              ) : (
                teachersList.map(({ teacher, poi }) => (
                  <Card
                    key={teacher.id}
                    onPress={() => openPoi(poi)}
                    style={styles.resultCard}
                    padded={false}
                  >
                    <View style={styles.resultRow}>
                      <PoiIcon type="office" size={42} />
                      <View style={styles.resultText}>
                        <Text
                          style={[styles.resultTitle, { color: theme.colors.text }]}
                          numberOfLines={1}
                        >
                          {teacher.name?.[language] || teacher.name?.ru}
                        </Text>
                        <Text
                          style={[styles.resultSubtitle, { color: theme.colors.textSecondary }]}
                          numberOfLines={1}
                        >
                          {teacher.subject?.[language] || teacher.subject?.ru}
                          {poi ? ' · ' + (poi.number || poi.name?.ru) : ''}
                        </Text>
                      </View>
                      <MaterialCommunityIcons
                        name="chevron-right"
                        size={22}
                        color={theme.colors.textTertiary}
                      />
                    </View>
                  </Card>
                ))
              )
            ) : results.length === 0 ? (
              <EmptyState
                icon="magnify-close"
                title={t('search.noResults')}
                hint={t('search.noResultsHint')}
              />
            ) : (
              results.map((poi) => {
                const floor = getFloor(poi.floorId);
                const building = getBuildingOfFloor(poi.floorId);
                const teacher = poi.teacherId ? getTeacher(poi.teacherId) : null;
                return (
                  <Card
                    key={poi.id}
                    onPress={() => openPoi(poi)}
                    style={styles.resultCard}
                    padded={false}
                  >
                    <View style={styles.resultRow}>
                      <PoiIcon type={poi.type} size={42} />
                      <View style={styles.resultText}>
                        <Text
                          style={[styles.resultTitle, { color: theme.colors.text }]}
                          numberOfLines={1}
                        >
                          {poi.number || poi.name?.[language] || poi.name?.ru}
                        </Text>
                        <Text
                          style={[styles.resultSubtitle, { color: theme.colors.textSecondary }]}
                          numberOfLines={1}
                        >
                          {formatFloorAndBuilding(floor, building, t, language)}
                          {teacher ? ' · ' + (teacher.name?.[language] || teacher.name?.ru) : ''}
                        </Text>
                      </View>
                      <MaterialCommunityIcons
                        name="chevron-right"
                        size={22}
                        color={theme.colors.textTertiary}
                      />
                    </View>
                  </Card>
                );
              })
            )}
          </View>
        ) : (
          <>
            {/* Быстрый поиск */}
            <View style={styles.section}>
              <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
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
                        backgroundColor:
                          category === item.id ? theme.colors.primarySoft : theme.colors.surfaceAlt,
                        borderColor:
                          category === item.id ? theme.colors.primary : theme.colors.surfaceAlt,
                        opacity: pressed ? 0.85 : 1,
                      },
                    ]}
                  >
                    <MaterialCommunityIcons name={item.icon} size={26} color={theme.colors.primary} />
                    <Text
                      style={[
                        styles.gridLabel,
                        { color: category === item.id ? theme.colors.primary : theme.colors.text },
                      ]}
                    >
                      {t('quick.' + item.id)}
                    </Text>
                  </Pressable>
                ))}
              </View>
            </View>

            {/* История поиска */}
            {history.length > 0 ? (
              <View style={styles.section}>
                <View style={styles.sectionHeader}>
                  <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>
                    {t('search.recentSearches')}
                  </Text>
                  <Pressable onPress={clear} hitSlop={8} accessibilityRole="button">
                    <Text style={[styles.clearText, { color: theme.colors.primary }]}>
                      {t('search.clearHistory')}
                    </Text>
                  </Pressable>
                </View>
                {history.map((item) => (
                  <View key={item} style={styles.historyRow}>
                    <Pressable
                      onPress={() => {
                        setQuery(item);
                        setSubmitted(item);
                        setCategory(null);
                      }}
                      style={({ pressed }) => [
                        styles.historySelect,
                        { opacity: pressed ? 0.6 : 1 },
                      ]}
                      accessibilityRole="button"
                    >
                      <MaterialCommunityIcons
                        name="history"
                        size={18}
                        color={theme.colors.textTertiary}
                      />
                      <Text
                        style={[styles.historyText, { color: theme.colors.text }]}
                        numberOfLines={1}
                      >
                        {item}
                      </Text>
                    </Pressable>
                    <Pressable
                      onPress={() => remove(item)}
                      hitSlop={8}
                      style={styles.historyRemove}
                      accessibilityRole="button"
                      accessibilityLabel={t('ui.clear')}
                    >
                      <MaterialCommunityIcons
                        name="close"
                        size={16}
                        color={theme.colors.textTertiary}
                      />
                    </Pressable>
                  </View>
                ))}
              </View>
            ) : (
              <EmptyState
                icon="magnify"
                title={t('search.emptyTitle')}
                hint={t('search.emptyHint')}
              />
            )}
          </>
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingBottom: 10,
  },
  // Без flex: 1 поле ввода в row-контейнере схлопывается и в него нельзя печатать
  searchBar: { flex: 1 },
  scroll: { flex: 1 },
  scrollContent: { padding: 12, paddingBottom: 40 },
  section: { marginBottom: 20 },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    marginBottom: 10,
  },
  sectionTitle: { fontSize: 17, fontWeight: '700' },
  clearText: { fontSize: 13, fontWeight: '600' },
  grid: { flexDirection: 'row', flexWrap: 'wrap', marginTop: 12 },
  gridItem: {
    width: '31%',
    aspectRatio: 1,
    borderRadius: 16,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: '2%',
    marginBottom: 10,
  },
  gridLabel: { fontSize: 12.5, fontWeight: '600', marginTop: 8, textAlign: 'center' },
  resultCard: { marginBottom: 8 },
  resultRow: { flexDirection: 'row', alignItems: 'center', padding: 12 },
  resultText: { flex: 1, marginLeft: 12 },
  resultTitle: { fontSize: 16, fontWeight: '700' },
  resultSubtitle: { fontSize: 12.5, marginTop: 2 },
  historyRow: { flexDirection: 'row', alignItems: 'center', paddingVertical: 3 },
  historySelect: { flex: 1, flexDirection: 'row', alignItems: 'center', paddingVertical: 8 },
  historyText: { flex: 1, fontSize: 14.5, marginLeft: 10 },
  historyRemove: { padding: 4 },
});

export default SearchScreen;
