/** Room/place details and route-start choices. */
import React, { useCallback, useMemo, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Alert,
  Modal,
  TextInput,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { Button } from '../components/Button';
import { ReferenceTabBar } from '../components/ReferenceTabBar';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { useFavorites } from '../hooks/useFavorites';
import {
  getPoi,
  getFloor,
  getBuildingOfFloor,
  getAllPois,
  getDefaultStartPoint,
} from '../data';
import { buildRoute } from '../services/pathfinding';

function roomName(poi, t, language) {
  if (poi.number) return `${t('poi.room')} ${poi.number}`;
  return poi.name?.[language] || poi.name?.ru || t('poiType.' + poi.type);
}

function roomDescription(poi, language) {
  const name = poi.name?.[language] || poi.name?.ru || '';
  if (!poi.number) return name;
  const withSubject = name.replace(new RegExp(`^(?:аудитория|кабинет|room|classroom)\\s*${poi.number}\\s*[—–:-]\\s*`, 'i'), '');
  if (withSubject !== name) return withSubject;
  return name.replace(new RegExp(`^(?:аудитория|кабинет|room|classroom)\\s*${poi.number}\\s*$`, 'i'), '');
}

export function PoiDetailScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const insets = useSafeAreaInsets();
  const { isFavorite, toggle } = useFavorites();
  const [pickerOpen, setPickerOpen] = useState(false);
  const [startQuery, setStartQuery] = useState('');

  const poi = getPoi(route.params?.poiId);
  const floor = poi ? getFloor(poi.floorId) : null;
  const building = poi ? getBuildingOfFloor(poi.floorId) : null;
  const favorite = poi ? isFavorite(poi.id) : false;
  const title = poi ? roomName(poi, t, language) : t('poi.notFound');
  const detailName = poi ? roomDescription(poi, language) : '';
  const entrance = useMemo(() => getDefaultStartPoint(), []);
  const cafeteria = useMemo(() => getAllPois().find((item) => item.type === 'cafeteria') || null, []);

  const startChoices = useMemo(() => {
    const queryText = startQuery.trim().toLowerCase().replace(/ё/g, 'е');
    return getAllPois()
      .filter((item) => item.id !== poi?.id && item.kind === 'room')
      .filter((item) => {
        if (!queryText) return true;
        const text = `${item.number || ''} ${item.name?.[language] || ''} ${item.name?.ru || ''} ${(item.aliases || []).join(' ')}`
          .toLowerCase()
          .replace(/ё/g, 'е');
        return text.includes(queryText);
      })
      .slice(0, 12);
  }, [language, poi?.id, startQuery]);

  const handleBuildRoute = useCallback((startPoint) => {
    if (!poi || !startPoint) return;
    const result = buildRoute(startPoint, { floorId: poi.floorId, x: poi.x, y: poi.y });
    if (!result.ok) {
      Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
      return;
    }
    navigation.navigate('Navigation', {
      routeData: result,
      startPoi: {
        id: startPoint.id || 'start',
        type: startPoint.type || 'entrance',
        floorId: startPoint.floorId,
        x: startPoint.x,
        y: startPoint.y,
      },
      endPoi: poi,
    });
  }, [navigation, poi, t]);

  const pickStart = useCallback((startPoi) => {
    setPickerOpen(false);
    setStartQuery('');
    handleBuildRoute(startPoi);
  }, [handleBuildRoute]);

  if (!poi) {
    return (
      <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
        <View style={[styles.topBar, { paddingTop: Math.max(insets.top + 7, 14) }]}>
          <BackButton onPress={() => navigation.goBack()} label={t('ui.back')} />
        </View>
        <Text style={[styles.notFound, { color: theme.colors.text }]}>{t('poi.notFound')}</Text>
      </View>
    );
  }

  const buildingName = building?.name?.[language] || building?.name?.ru || '';
  const routeStart = (id, point) => {
    if (!point) {
      Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
      return;
    }
    handleBuildRoute({ ...point, id, type: point.type || 'entrance' });
  };

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={[styles.topBar, { paddingTop: Math.max(insets.top + 7, 14) }]}>
        <BackButton onPress={() => navigation.goBack()} label={t('ui.back')} />
        <View style={{ flex: 1 }} />
        <Pressable
          onPress={() => toggle(poi.id)}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel={favorite ? t('poi.removeFromFavorites') : t('poi.addToFavorites')}
          style={styles.favoriteButton}
        >
          <MaterialCommunityIcons
            name={favorite ? 'thumb-up' : 'thumb-up-outline'}
            size={23}
            color={theme.colors.primary}
          />
        </Pressable>
      </View>

      <ScrollView contentContainerStyle={styles.scroll} keyboardShouldPersistTaps="handled">
        <View style={[styles.heroCard, { backgroundColor: theme.colors.surfaceAlt }]}>
          <Text style={[styles.heroTitle, { color: theme.colors.text }]}>{title}</Text>
          <Text style={[styles.heroSubtitle, { color: theme.colors.textTertiary }]}>{buildingName}</Text>
          {floor ? (
            <Text style={[styles.heroSubtitle, { color: theme.colors.textTertiary }]}>
              {t('ui.floor')} {floor.level}
            </Text>
          ) : null}
        </View>

        <View style={styles.metadata}>
          <MetadataRow
            icon="office-building-outline"
            label={detailName || t('poiType.' + poi.type)}
            theme={theme}
          />
          <MetadataRow
            icon="stairs"
            label={floor ? `${t('ui.floor')} ${floor.level}` : t('poiType.' + poi.type)}
            theme={theme}
          />
        </View>

        <Button
          title={t('ui.buildRoute')}
          icon="map-marker-path"
          size="lg"
          fullWidth
          onPress={() => routeStart('entrance', entrance)}
          style={styles.primaryButton}
        />

        <Text style={[styles.sectionTitle, { color: theme.colors.text }]}>{t('poi.routeStartHeading')}</Text>
        <View style={[styles.startOptions, { borderColor: theme.colors.border, backgroundColor: theme.colors.surface }]}>
          <StartOption
            icon="map-marker-radius-outline"
            label={t('poi.routeFromHere')}
            selected
            onPress={() => routeStart('gps-start', entrance)}
            theme={theme}
          />
          <StartOption
            icon="login-variant"
            label={t('poi.routeFromMainEntrance')}
            onPress={() => routeStart('entrance', entrance)}
            theme={theme}
          />
          {cafeteria ? (
            <StartOption
              icon="silverware-fork-knife"
              label={t('poi.routeFromCafeteria')}
              onPress={() => routeStart(cafeteria.id, cafeteria)}
              theme={theme}
            />
          ) : null}
          <StartOption
            icon="map-marker-plus-outline"
            label={t('poi.chooseStartPoint')}
            onPress={() => setPickerOpen(true)}
            theme={theme}
            last
          />
        </View>
      </ScrollView>

      <ReferenceTabBar navigation={navigation} active="SearchTab" />

      <Modal
        visible={pickerOpen}
        transparent
        animationType="slide"
        onRequestClose={() => setPickerOpen(false)}
      >
        <View style={styles.modalRoot}>
          <Pressable style={styles.modalBackdrop} onPress={() => setPickerOpen(false)} />
          <View style={[styles.pickerSheet, { backgroundColor: theme.colors.surface }]}>
            <View style={styles.pickerHandle} />
            <View style={styles.pickerHeading}>
              <Text style={[styles.pickerTitle, { color: theme.colors.text }]}>{t('poi.chooseStartPoint')}</Text>
              <Pressable onPress={() => setPickerOpen(false)} hitSlop={10} accessibilityRole="button">
                <MaterialCommunityIcons name="close" size={22} color={theme.colors.textSecondary} />
              </Pressable>
            </View>
            <View style={[styles.pickerSearch, { backgroundColor: theme.colors.surfaceAlt }]}>
              <MaterialCommunityIcons name="magnify" size={19} color={theme.colors.primary} />
              <TextInput
                value={startQuery}
                onChangeText={setStartQuery}
                placeholder={t('search.placeholder')}
                placeholderTextColor={theme.colors.textTertiary}
                style={[styles.pickerInput, { color: theme.colors.text }]}
                returnKeyType="search"
              />
            </View>
            <ScrollView keyboardShouldPersistTaps="handled">
              {startChoices.map((choice) => (
                <Pressable
                  key={choice.id}
                  onPress={() => pickStart(choice)}
                  accessibilityRole="button"
                  style={({ pressed }) => [styles.choiceRow, { borderBottomColor: theme.colors.border, opacity: pressed ? 0.6 : 1 }]}
                >
                  <MaterialCommunityIcons name="office-building-outline" size={19} color={theme.colors.primary} />
                  <Text style={[styles.choiceText, { color: theme.colors.text }]}>
                    {roomName(choice, t, language)}
                  </Text>
                  <Text style={[styles.choiceFloor, { color: theme.colors.textTertiary }]}>
                    {t('ui.floor')} {getFloor(choice.floorId)?.level}
                  </Text>
                </Pressable>
              ))}
              {startChoices.length === 0 ? (
                <Text style={[styles.noChoices, { color: theme.colors.textSecondary }]}>{t('search.noResults')}</Text>
              ) : null}
            </ScrollView>
          </View>
        </View>
      </Modal>
    </View>
  );
}

function BackButton({ onPress, label }) {
  const { theme } = useTheme();
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label} hitSlop={8} style={styles.backButton}>
      <MaterialCommunityIcons name="chevron-left" size={29} color={theme.colors.primary} />
    </Pressable>
  );
}

function MetadataRow({ icon, label, theme }) {
  return (
    <View style={styles.metadataRow}>
      <MaterialCommunityIcons name={icon} size={21} color={theme.colors.primary} />
      <Text style={[styles.metadataLabel, { color: theme.colors.text }]} numberOfLines={2}>{label}</Text>
    </View>
  );
}

function StartOption({ icon, label, onPress, selected, last, theme }) {
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      style={({ pressed }) => [
        styles.startOption,
        {
          backgroundColor: selected ? theme.colors.primarySoft : theme.colors.surface,
          borderBottomColor: theme.colors.border,
          opacity: pressed ? 0.74 : 1,
        },
        last && styles.lastStartOption,
      ]}
    >
      <MaterialCommunityIcons
        name={icon}
        size={25}
        color={selected ? theme.colors.primary : theme.colors.text}
      />
      <Text style={[styles.startOptionText, { color: selected ? theme.colors.primary : theme.colors.text }]}>
        {label}
      </Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  topBar: { flexDirection: 'row', alignItems: 'center', paddingHorizontal: 28, paddingBottom: 12 },
  backButton: { width: 38, height: 38, justifyContent: 'center', alignItems: 'flex-start' },
  favoriteButton: { width: 40, height: 38, alignItems: 'flex-end', justifyContent: 'center' },
  scroll: { paddingHorizontal: 28, paddingBottom: 104 },
  heroCard: { borderRadius: 15, paddingHorizontal: 20, paddingVertical: 17, marginBottom: 18 },
  heroTitle: { fontSize: 25, lineHeight: 31, fontWeight: '800', letterSpacing: -0.35 },
  heroSubtitle: { fontSize: 13.5, lineHeight: 18, marginTop: 1 },
  metadata: { marginBottom: 18 },
  metadataRow: { minHeight: 32, flexDirection: 'row', alignItems: 'center', marginBottom: 5 },
  metadataLabel: { flex: 1, fontSize: 15, fontWeight: '600', marginLeft: 15 },
  primaryButton: { minHeight: 48, marginBottom: 32 },
  sectionTitle: { fontSize: 21, fontWeight: '800', marginBottom: 13, letterSpacing: -0.3 },
  startOptions: { borderWidth: 1, borderRadius: 18, overflow: 'hidden' },
  startOption: { minHeight: 59, flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, borderBottomWidth: StyleSheet.hairlineWidth },
  lastStartOption: { borderBottomWidth: 0 },
  startOptionText: { fontSize: 15, fontWeight: '700', marginLeft: 16 },
  notFound: { fontSize: 18, fontWeight: '700', padding: 28 },
  modalRoot: { flex: 1, justifyContent: 'flex-end' },
  modalBackdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(15,16,32,0.35)' },
  pickerSheet: { maxHeight: '78%', minHeight: '45%', borderTopLeftRadius: 26, borderTopRightRadius: 26, paddingHorizontal: 22, paddingBottom: 24 },
  pickerHandle: { alignSelf: 'center', width: 42, height: 5, borderRadius: 3, backgroundColor: '#C7C9D1', marginTop: 10, marginBottom: 18 },
  pickerHeading: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginBottom: 13 },
  pickerTitle: { fontSize: 20, fontWeight: '800' },
  pickerSearch: { flexDirection: 'row', alignItems: 'center', borderRadius: 14, paddingHorizontal: 13, height: 46, marginBottom: 8 },
  pickerInput: { flex: 1, height: 44, marginLeft: 9, fontSize: 14 },
  choiceRow: { minHeight: 50, flexDirection: 'row', alignItems: 'center', borderBottomWidth: StyleSheet.hairlineWidth },
  choiceText: { flex: 1, fontSize: 14, fontWeight: '600', marginLeft: 12 },
  choiceFloor: { fontSize: 11 },
  noChoices: { paddingVertical: 25, textAlign: 'center' },
});

export default PoiDetailScreen;
