/**
 * screens/MapScreen.js — главный экран: интерактивная карта колледжа.
 *
 * Показывает только внутренние планы этажей (Map/map1lower.svg, Map/map2lower.svg):
 * выбор этажа, фильтры точек, приближение, карточка точки и построение маршрута.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { View, Text, StyleSheet, Pressable, ScrollView, useWindowDimensions } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { MapCanvas } from '../components/MapCanvas';
import { FloorSelector } from '../components/FloorSelector';
import { BottomSheet, getSheetHeight } from '../components/BottomSheet';
import { Button } from '../components/Button';
import { Chip } from '../components/Chip';
import { SearchBar } from '../components/SearchBar';
import { PoiIcon } from '../components/PoiIcon';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { useFavorites } from '../hooks/useFavorites';
import { MAP_FILTERS } from '../theme/poiTypes';
import { getAllPois, getPoi, getFloor, getBuildingOfFloor, getDefaultStartPoint } from '../data';
import { buildRoute } from '../services/pathfinding';
import { formatDistance, formatDuration, formatFloorAndBuilding } from '../utils/format';
import { getLastFloor, setLastFloor } from '../services/storage';

/** Высота строки чипсов-фильтров вместе с отступами */
const FILTERS_ROW_HEIGHT = 46;

export function MapScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const insets = useSafeAreaInsets();
  const { height: screenHeight } = useWindowDimensions();
  const { isFavorite, toggle } = useFavorites();

  const mapRef = useRef(null);
  const [floorId, setFloorId] = useState('main-1');
  const [selectedPoi, setSelectedPoi] = useState(null);
  const [activeRoute, setActiveRoute] = useState(null);
  const [routeStart, setRouteStart] = useState(null);
  const [routeEnd, setRouteEnd] = useState(null);
  const [filterId, setFilterId] = useState('all');
  const [sheetCollapsed, setSheetCollapsed] = useState(true);
  const [routeError, setRouteError] = useState(null);

  // --- Восстановление последнего открытого этажа ---------------------------
  useEffect(() => {
    let cancelled = false;
    (async () => {
      const last = await getLastFloor();
      if (!cancelled && last && getFloor(last)) setFloorId(last);
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  // --- Параметры навигации: фокус на точке --------------------------------
  useEffect(() => {
    const poiId = route?.params?.focusPoiId;
    if (!poiId) return;
    const poi = getPoi(poiId);
    if (poi && getFloor(poi.floorId)) {
      setFloorId(poi.floorId);
      setLastFloor(poi.floorId);
      setSelectedPoi(poi);
      setSheetCollapsed(false);
      navigation.setParams({ focusPoiId: undefined });
    }
  }, [route?.params?.focusPoiId, navigation]);

  // --- POI на текущем этаже (с учётом фильтра) -----------------------------
  const filterTypes = useMemo(() => {
    const filter = MAP_FILTERS.find((item) => item.id === filterId);
    return filter && filter.types ? filter.types : null;
  }, [filterId]);

  const visiblePois = useMemo(() => {
    let list = getAllPois().filter((poi) => poi.floorId === floorId);
    if (filterTypes) {
      const set = new Set(filterTypes);
      list = list.filter((poi) => set.has(poi.type));
    }
    return list;
  }, [floorId, filterTypes]);

  // --- Действия ------------------------------------------------------------

  const changeFloor = useCallback((nextFloorId) => {
    setFloorId(nextFloorId);
    setLastFloor(nextFloorId);
    setSelectedPoi(null);
    setRouteError(null);
  }, []);

  const handlePoiPress = useCallback((poi) => {
    setSelectedPoi(poi);
    setSheetCollapsed(false);
  }, []);

  const handleBuildRoute = useCallback((destinationPoi) => {
    if (!destinationPoi) return;
    const start = getDefaultStartPoint();
    if (!start) {
      setRouteError('routeNotFound');
      return;
    }
    const result = buildRoute(start, {
      floorId: destinationPoi.floorId,
      x: destinationPoi.x,
      y: destinationPoi.y,
    });
    if (!result.ok) {
      setRouteError(result.error || 'routeNotFound');
      setActiveRoute(null);
      setSheetCollapsed(false);
      return;
    }
    setRouteError(null);
    setActiveRoute(result);
    setRouteStart(start);
    setRouteEnd(destinationPoi);
    setFloorId(destinationPoi.floorId);
    setLastFloor(destinationPoi.floorId);
    setSheetCollapsed(false);
    const pointsOnFloor = result.points.filter(
      (point) => point.floorId === destinationPoi.floorId
    );
    setTimeout(() => {
      mapRef.current?.fitToPoints(pointsOnFloor);
    }, 350);
  }, []);

  const handleResetRoute = useCallback(() => {
    setActiveRoute(null);
    setRouteStart(null);
    setRouteEnd(null);
    setRouteError(null);
    mapRef.current?.resetView();
  }, []);

  const openSearch = useCallback(
    () => navigation.navigate('SearchTab', { fromMap: true }),
    [navigation]
  );

  // --- Нижняя панель -------------------------------------------------------
  const renderSheetContent = () => {
    if (routeError && !activeRoute) {
      return (
        <View style={styles.sheetBody}>
          <Text style={[styles.sheetTitle, { color: theme.colors.text }]}>{t('ui.routeNotFound')}</Text>
          <Text style={[styles.sheetSubtitle, { color: theme.colors.textSecondary }]}>
            {t('ui.routeNotFoundHint')}
          </Text>
          <Button
            title={t('ui.cancel')}
            variant="secondary"
            onPress={handleResetRoute}
            style={{ marginTop: 12 }}
          />
        </View>
      );
    }

    if (activeRoute && routeEnd) {
      return (
        <ScrollView style={styles.sheetScroll} contentContainerStyle={styles.sheetBody}>
          <View style={styles.routeHeader}>
            <PoiIcon type={routeEnd.type} size={42} />
            <View style={styles.routeHeaderText}>
              <Text style={[styles.sheetTitle, { color: theme.colors.text }]} numberOfLines={1}>
                {routeEnd.number || routeEnd.name?.[language] || routeEnd.name?.ru}
              </Text>
              <View style={styles.routeMetrics}>
                <MaterialCommunityIcons name="walk" size={16} color={theme.colors.primary} />
                <Text style={[styles.metricsText, { color: theme.colors.primary }]}>
                  {formatDuration(activeRoute.durationSec, t)} ·{' '}
                  {formatDistance(activeRoute.distanceM, t)}
                </Text>
              </View>
              <Text
                style={[styles.sheetSubtitle, { color: theme.colors.textSecondary }]}
                numberOfLines={1}
              >
                {formatFloorAndBuilding(
                  getFloor(routeEnd.floorId),
                  getBuildingOfFloor(routeEnd.floorId),
                  t,
                  language
                )}
              </Text>
            </View>
          </View>

          <View style={styles.routeActions}>
            <Button
              title={t('ui.startRoute')}
              icon="navigation-variant"
              onPress={() =>
                navigation.navigate('Navigation', {
                  routeData: activeRoute,
                  startPoi: routeStart,
                  endPoi: routeEnd,
                })
              }
              style={{ flex: 1 }}
            />
            <Button title={t('ui.clear')} variant="secondary" onPress={handleResetRoute} />
          </View>
        </ScrollView>
      );
    }

    if (selectedPoi) {
      const floor = getFloor(selectedPoi.floorId);
      const building = getBuildingOfFloor(selectedPoi.floorId);
      const favorite = isFavorite(selectedPoi.id);
      return (
        <ScrollView style={styles.sheetScroll} contentContainerStyle={styles.sheetBody}>
          <Pressable
            onPress={() => navigation.navigate('PoiDetail', { poiId: selectedPoi.id })}
            style={styles.selectedHeader}
            accessibilityRole="button"
          >
            <PoiIcon type={selectedPoi.type} size={46} />
            <View style={styles.routeHeaderText}>
              <Text style={[styles.sheetTitle, { color: theme.colors.text }]} numberOfLines={1}>
                {selectedPoi.number || selectedPoi.name?.[language] || selectedPoi.name?.ru}
              </Text>
              <Text
                style={[styles.sheetSubtitle, { color: theme.colors.textSecondary }]}
                numberOfLines={1}
              >
                {formatFloorAndBuilding(floor, building, t, language)}
              </Text>
            </View>
            <MaterialCommunityIcons
              name="chevron-right"
              size={24}
              color={theme.colors.textTertiary}
            />
          </Pressable>

          <View style={styles.routeActions}>
            <Button
              title={t('ui.buildRoute')}
              icon="navigation-variant"
              onPress={() => handleBuildRoute(selectedPoi)}
              style={{ flex: 1 }}
            />
            <Button
              title={favorite ? t('favorites.remove') : t('poi.addToFavorites')}
              icon={favorite ? 'heart' : 'heart-outline'}
              variant="secondary"
              onPress={() => toggle(selectedPoi.id)}
            />
          </View>
        </ScrollView>
      );
    }

    return (
      <View style={styles.sheetBody}>
        <Text style={[styles.sheetTitle, { color: theme.colors.text }]}>{t('map.title')}</Text>
        <Text style={[styles.sheetSubtitle, { color: theme.colors.textSecondary }]}>
          {t('map.tapMarker')}
        </Text>
      </View>
    );
  };

  // --- Размещение плавающих элементов над нижней панелью -------------------
  const sheetHeight = getSheetHeight(sheetCollapsed, screenHeight);
  const filtersBottom = sheetHeight + 12;
  const buttonsBottom = filtersBottom + FILTERS_ROW_HEIGHT;

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <MapCanvas
        ref={mapRef}
        floorId={floorId}
        pois={visiblePois}
        route={activeRoute}
        startPoi={routeStart ? { id: 'start', type: 'entrance', x: routeStart.x, y: routeStart.y } : null}
        endPoi={routeEnd}
        selectedPoiId={selectedPoi?.id}
        onPoiPress={handlePoiPress}
        dimMarkers={!!activeRoute}
      />

      {/* Верхняя панель: поиск + выбор этажа */}
      <View style={[styles.topBar, { paddingTop: insets.top + 8 }]} pointerEvents="box-none">
        <SearchBar
          value=""
          onChangeText={() => {}}
          onPress={openSearch}
          editable={false}
          placeholder={t('map.quickSearchPlaceholder')}
        />
        <View style={styles.floorRow}>
          <FloorSelector floorId={floorId} onChange={changeFloor} />
        </View>
      </View>

      {/* Чипсы фильтров */}
      <View style={[styles.filterRow, { bottom: filtersBottom }]} pointerEvents="box-none">
        <ScrollView
          horizontal
          showsHorizontalScrollIndicator={false}
          contentContainerStyle={styles.filterScroll}
        >
          {MAP_FILTERS.map((filter) => (
            <Chip
              key={filter.id}
              label={t('filter.' + filter.id)}
              icon={filter.icon}
              selected={filter.id === filterId}
              onPress={() => setFilterId(filter.id)}
              style={styles.filterChip}
            />
          ))}
        </ScrollView>
      </View>

      {/* Плавающие кнопки масштаба */}
      <View style={[styles.floatingColumn, { bottom: buttonsBottom }]} pointerEvents="box-none">
        <Pressable
          onPress={() => mapRef.current?.zoomIn()}
          accessibilityRole="button"
          accessibilityLabel={t('map.zoomIn')}
          style={[styles.fab, { backgroundColor: theme.colors.surface, borderColor: theme.colors.border }]}
        >
          <MaterialCommunityIcons name="plus" size={22} color={theme.colors.text} />
        </Pressable>
        <Pressable
          onPress={() => mapRef.current?.zoomOut()}
          accessibilityRole="button"
          accessibilityLabel={t('map.zoomOut')}
          style={[styles.fab, { backgroundColor: theme.colors.surface, borderColor: theme.colors.border }]}
        >
          <MaterialCommunityIcons name="minus" size={22} color={theme.colors.text} />
        </Pressable>
        <Pressable
          onPress={() => mapRef.current?.resetView()}
          accessibilityRole="button"
          accessibilityLabel={t('map.resetView')}
          style={[styles.fab, styles.fabPrimary, { backgroundColor: theme.colors.primary }]}
        >
          <MaterialCommunityIcons name="fit-to-screen-outline" size={22} color={theme.colors.primaryText} />
        </Pressable>
      </View>

      {/* Нижняя панель */}
      <BottomSheet collapsed={sheetCollapsed} onToggle={() => setSheetCollapsed((v) => !v)}>
        {renderSheetContent()}
      </BottomSheet>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  topBar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 12,
    zIndex: 10,
  },
  floorRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 8,
  },
  floatingColumn: {
    position: 'absolute',
    right: 12,
    alignItems: 'center',
    zIndex: 5,
  },
  fab: {
    width: 42,
    height: 42,
    borderRadius: 21,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 8,
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 8,
    shadowOffset: { width: 0, height: 3 },
    elevation: 4,
  },
  fabPrimary: { borderColor: 'transparent', marginBottom: 0 },
  filterRow: {
    position: 'absolute',
    left: 0,
    right: 0,
    zIndex: 5,
  },
  filterScroll: { paddingHorizontal: 12 },
  filterChip: { marginRight: 8 },
  sheetScroll: { flexGrow: 0 },
  sheetBody: { padding: 16, paddingTop: 4 },
  sheetTitle: { fontSize: 18, fontWeight: '700' },
  sheetSubtitle: { fontSize: 13.5, marginTop: 3 },
  selectedHeader: { flexDirection: 'row', alignItems: 'center' },
  routeHeader: { flexDirection: 'row', alignItems: 'center' },
  routeHeaderText: { flex: 1, marginLeft: 12 },
  routeMetrics: { flexDirection: 'row', alignItems: 'center', marginTop: 4 },
  metricsText: { fontSize: 14, fontWeight: '700', marginLeft: 5 },
  routeActions: { flexDirection: 'row', alignItems: 'center', marginTop: 16 },
});

export default MapScreen;
