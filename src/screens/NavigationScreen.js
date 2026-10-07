/**
 * screens/NavigationScreen.js — экран маршрута.
 *
 * Показывает маршрут на плане этажа, пошаговые инструкции и режим
 * пошаговой навигации («Идите прямо 60 м» → «Далее»).
 * При смене этажа на маршруте план переключается автоматически.
 */
import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
} from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useFocusEffect } from '@react-navigation/native';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { FloorSelector } from '../components/FloorSelector';
import { MapCanvas } from '../components/MapCanvas';
import { Button } from '../components/Button';
import { PoiIcon } from '../components/PoiIcon';
import { RouteStepItem, stepText } from '../components/RouteStepItem';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { getAllPois, getFloor, getBuildingOfFloor } from '../data';
import { buildRoute } from '../services/pathfinding';
import { getNavGraph } from '../services/graph';
import { formatDistance, formatDuration, roundMeters } from '../utils/format';
import { useGpsLocation } from '../hooks/useGpsLocation';
import { gpsToPlanLocation } from '../services/geolocation';

export function NavigationScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const insets = useSafeAreaInsets();
  const mapRef = useRef(null);

  const initialRouteData = route.params?.routeData;
  const endPoi = route.params?.endPoi;
  const [routeData, setRouteData] = useState(initialRouteData);
  const [startPoi, setStartPoi] = useState(route.params?.startPoi);

  const [floorId, setFloorId] = useState(initialRouteData?.floors?.[0] || endPoi?.floorId || 'main-1');
  const [navigating, setNavigating] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);

  const steps = routeData?.steps || [];
  const pois = useMemo(() => getAllPois(), []);
  const { status: gpsStatus, location: gpsLocation, start: startGps, stop: stopGps } = useGpsLocation();

  const planLocation = useMemo(() => {
    const coords = gpsLocation?.coords;
    if (!coords) return null;
    return gpsToPlanLocation(floorId, coords.latitude, coords.longitude, coords.accuracy);
  }, [floorId, gpsLocation]);

  const gpsStatusText = useMemo(() => {
    if (gpsStatus === 'requesting') return t('map.gpsRequesting');
    if (gpsStatus === 'locating') return t('map.gpsLocating');
    if (gpsStatus === 'denied') return t('map.gpsPermissionDenied');
    if (gpsStatus === 'error') return t('map.gpsFailed');
    if (gpsStatus === 'tracking') {
      if (!planLocation?.withinPlan) return t('map.gpsOutsidePlan');
      return `${t('map.gpsAccuracy', { accuracy: Math.round(planLocation.accuracyMeters ?? 0) })} · ${t('navigation.gpsFloorHint')}`;
    }
    return t('navigation.gpsHint');
  }, [gpsStatus, planLocation, t]);

  useFocusEffect(
    useCallback(() => () => stopGps(), [stopGps])
  );

  // --- Подгоняем вид под маршрут при открытии и смене этажа -----------------
  useEffect(() => {
    const timer = setTimeout(() => {
      mapRef.current?.fitToPoints(routeData?.points || []);
    }, 300);
    return () => clearTimeout(timer);
  }, [routeData]);

  // --- Автопереключение этажа под текущий шаг ------------------------------
  useEffect(() => {
    const step = steps[currentStep];
    if (step && step.floorId && step.floorId !== floorId) {
      setFloorId(step.floorId);
    }
  }, [currentStep, steps, floorId]);

  // GPS-положение показывается на плане; внутри корпуса оно не продвигает
  // маршрут автоматически, так как точность GPS может быть недостаточной.

  const goNext = useCallback(() => {
    setCurrentStep((prev) => Math.min(prev + 1, steps.length - 1));
  }, [steps.length]);

  const goPrev = useCallback(() => {
    setCurrentStep((prev) => Math.max(prev - 1, 0));
  }, []);

  const startNavigation = useCallback(async () => {
    setCurrentStep(0);
    setNavigating(true);
    const fix = gpsStatus === 'tracking' ? gpsLocation : await startGps();
    const coords = fix?.coords;
    const startFloorId = routeData?.floors?.[0] || floorId;
    const accuracyIsUsable =
      !Number.isFinite(coords?.accuracy) || coords.accuracy <= 20;

    if (coords && accuracyIsUsable) {
      const point = gpsToPlanLocation(startFloorId, coords.latitude, coords.longitude, coords.accuracy);
      const nearWalkableArea = point?.withinPlan
        && getNavGraph().snapToWalkable(startFloorId, point.x, point.y, 12);
      if (nearWalkableArea) {
        const liveRoute = buildRoute(
          { floorId: startFloorId, x: point.x, y: point.y },
          { floorId: endPoi.floorId, x: endPoi.x, y: endPoi.y }
        );
        if (liveRoute.ok) {
          setRouteData(liveRoute);
          setStartPoi({ ...point, id: 'gps-start', type: 'entrance' });
          setFloorId(startFloorId);
        }
      }
    }
  }, [endPoi, floorId, gpsLocation, gpsStatus, routeData, startGps]);

  const handleLocateMe = useCallback(async () => {
    const fix = gpsStatus === 'tracking' ? gpsLocation : await startGps();
    const coords = fix?.coords;
    if (!coords) return;
    const point = gpsToPlanLocation(floorId, coords.latitude, coords.longitude, coords.accuracy);
    if (point?.withinPlan) mapRef.current?.fitToPoints([point]);
  }, [floorId, gpsLocation, gpsStatus, startGps]);

  const stopNavigation = useCallback(() => {
    setNavigating(false);
    setCurrentStep(0);
    stopGps();
  }, [stopGps]);

  if (!routeData || !endPoi) {
    return (
      <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
        <Text style={{ color: theme.colors.text, padding: 20 }}>{t('ui.routeNotFound')}</Text>
      </View>
    );
  }

  const currentStepData = steps[currentStep];
  const isLastStep = currentStep >= steps.length - 1;

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      {/* Карта с маршрутом */}
      <View style={styles.mapArea}>
        <MapCanvas
          ref={mapRef}
          floorId={floorId}
          pois={pois.filter((poi) => poi.floorId === floorId)}
          route={routeData}
          startPoi={startPoi}
          endPoi={endPoi}
          selectedPoiId={endPoi.id}
          userLocation={gpsStatus === 'tracking' ? planLocation : null}
          dimMarkers
        />

        {/* Верхняя панель: заголовок и ручной выбор этажа */}
        <View style={[styles.mapTopBar, { paddingTop: Math.max(insets.top + 8, 14) }]} pointerEvents="box-none">
          <View style={styles.mapHeaderRow}>
            <Pressable
              onPress={() => navigation.goBack()}
              accessibilityRole="button"
              accessibilityLabel={t('ui.back')}
              style={styles.headerBackButton}
            >
              <MaterialCommunityIcons name="chevron-left" size={29} color={theme.colors.primary} />
            </Pressable>
            <Text style={[styles.mapHeaderTitle, { color: theme.colors.text }]}>{t('map.title')}</Text>
            <View style={styles.headerBackButton} />
          </View>
          <View style={styles.mapControlsRow}>
            <FloorSelector floorId={floorId} onChange={setFloorId} />
            <View style={{ flex: 1 }} />
            <Pressable
              onPress={handleLocateMe}
              accessibilityRole="button"
              accessibilityLabel={t('map.locateMe')}
              style={[
                styles.circleButton,
                { backgroundColor: gpsStatus === 'tracking' ? theme.colors.primary : theme.colors.surface },
              ]}
            >
              <MaterialCommunityIcons
                name={gpsStatus === 'tracking' ? 'crosshairs-gps' : 'crosshairs'}
                size={20}
                color={gpsStatus === 'tracking' ? theme.colors.primaryText : theme.colors.info}
              />
            </Pressable>
            <Pressable
              onPress={() => mapRef.current?.fitToPoints(routeData.points)}
              accessibilityRole="button"
              accessibilityLabel={t('map.resetView')}
              style={[styles.circleButton, { backgroundColor: theme.colors.surface, marginLeft: 8 }]}
            >
              <MaterialCommunityIcons name="fit-to-page-outline" size={20} color={theme.colors.text} />
            </Pressable>
          </View>
        </View>

        {/* Пошаговый режим: крупная подсказка */}
        {navigating && currentStepData ? (
          <View
            style={[styles.stepBanner, { top: Math.max(insets.top + 96, 108), backgroundColor: theme.colors.primary }]}
            pointerEvents="box-none"
          >
            <Text style={styles.stepBannerText}>{stepText(currentStepData, t)}</Text>
            {currentStepData.distanceM > 0.5 ? (
              <Text style={styles.stepBannerDistance}>
                {roundMeters(currentStepData.distanceM)} {t('ui.m')}
              </Text>
            ) : null}
          </View>
        ) : null}
      </View>

      {/* Нижняя панель */}
      <View
        style={[
          styles.panel,
          { backgroundColor: theme.colors.surface, borderTopColor: theme.colors.border },
        ]}
      >
        <View style={styles.panelHandleArea}>
          <View style={[styles.panelHandle, { backgroundColor: theme.colors.borderStrong }]} />
        </View>

        <ScrollView style={{ flex: 1 }} contentContainerStyle={styles.panelScroll}>
          {/* Заголовок маршрута */}
          <View style={styles.destinationRow}>
            {endPoi.kind !== 'room' ? <PoiIcon type={endPoi.type} size={42} /> : null}
            <View style={[styles.destinationText, endPoi.kind === 'room' && { marginLeft: 0 }]}>
              <Text style={[styles.destinationTitle, { color: theme.colors.text }]} numberOfLines={1}>
                {endPoi.number ? `${t('poi.room')} ${endPoi.number}` : endPoi.name?.[language] || endPoi.name?.ru}
              </Text>
              <Text style={[styles.destinationSubtitle, { color: theme.colors.textSecondary }]} numberOfLines={1}>
                {t('ui.floor')} {getFloor(endPoi.floorId)?.level ?? ''} ·{' '}
                {getBuildingOfFloor(endPoi.floorId)?.name?.[language] || getBuildingOfFloor(endPoi.floorId)?.name?.ru || ''}
              </Text>
            </View>
            <View style={styles.metrics}>
              <MaterialCommunityIcons name="walk" size={17} color={theme.colors.primary} />
              <Text style={[styles.metricValue, { color: theme.colors.text }]}>
                {formatDuration(routeData.durationSec, t)} · {formatDistance(routeData.distanceM, t)}
              </Text>
            </View>
          </View>

          <View style={[styles.gpsHint, { backgroundColor: theme.colors.surfaceAlt }]}>
            <MaterialCommunityIcons name="crosshairs-gps" size={16} color={theme.colors.info} />
            <Text style={[styles.gpsHintText, { color: theme.colors.textSecondary }]}>
              {gpsStatusText}
            </Text>
          </View>

          {/* Пошаговая инструкция */}
          {navigating ? (
            <View style={styles.navControls}>
              <Button
                title={t('ui.prev')}
                icon="chevron-left"
                variant="secondary"
                onPress={goPrev}
                disabled={currentStep === 0}
                style={{ flex: 1 }}
              />
              <Button
                title={isLastStep ? t('ui.done') : t('ui.next')}
                icon={isLastStep ? 'check' : 'chevron-right'}
                onPress={isLastStep ? stopNavigation : goNext}
                style={{ flex: 1.4 }}
              />
              <Button
                title={t('ui.stopRoute')}
                icon="stop"
                variant="secondary"
                onPress={stopNavigation}
                style={{ flex: 1 }}
              />
            </View>
          ) : null}

          <Text style={[styles.stepsTitle, { color: theme.colors.text }]}>{t('ui.steps')}</Text>
          {steps.map((step, index) => (
            <RouteStepItem
              key={index}
              step={step}
              index={index}
              active={navigating && index === currentStep}
              compact={false}
            />
          ))}

          {!navigating ? (
            <Button
              title={t('ui.startRoute')}
              icon="navigation-variant"
              size="lg"
              fullWidth
              onPress={startNavigation}
              style={styles.startRouteButton}
            />
          ) : null}
        </ScrollView>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  mapArea: { flex: 1 },
  mapTopBar: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    paddingHorizontal: 22,
    zIndex: 8,
  },
  mapHeaderRow: { flexDirection: 'row', alignItems: 'center', minHeight: 38 },
  headerBackButton: { width: 38, height: 38, alignItems: 'flex-start', justifyContent: 'center' },
  mapHeaderTitle: { flex: 1, textAlign: 'center', fontSize: 20, fontWeight: '800', letterSpacing: -0.25 },
  mapControlsRow: { flexDirection: 'row', alignItems: 'center', marginTop: 8 },
  circleButton: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.08,
    shadowRadius: 5,
    shadowOffset: { width: 0, height: 2 },
    elevation: 2,
  },
  stepBanner: {
    position: 'absolute',
    top: 108,
    left: 16,
    right: 16,
    borderRadius: 17,
    padding: 15,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.16,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 5 },
    elevation: 7,
  },
  stepBannerText: { color: '#FFFFFF', fontSize: 19, fontWeight: '800', textAlign: 'center' },
  stepBannerDistance: { color: 'rgba(255,255,255,0.85)', fontSize: 14, marginTop: 4, fontWeight: '600' },
  panel: {
    height: '47%',
    borderTopLeftRadius: 25,
    borderTopRightRadius: 25,
    borderTopWidth: 1,
  },
  panelHandleArea: { alignItems: 'center', paddingTop: 8, paddingBottom: 3 },
  panelHandle: { width: 44, height: 5, borderRadius: 3 },
  panelScroll: { paddingHorizontal: 20, paddingTop: 10, paddingBottom: 28 },
  destinationRow: { flexDirection: 'row', alignItems: 'center', flexWrap: 'wrap' },
  destinationText: { flex: 1, minWidth: 125, marginLeft: 12 },
  destinationTitle: { fontSize: 19, fontWeight: '800', letterSpacing: -0.2 },
  destinationSubtitle: { fontSize: 12.5, marginTop: 2 },
  metrics: { flexDirection: 'row', alignItems: 'center', marginLeft: 'auto', marginTop: 7 },
  metricValue: { fontSize: 13, fontWeight: '700', marginLeft: 5, flexShrink: 1 },
  gpsHint: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    paddingHorizontal: 10,
    paddingVertical: 8,
    borderRadius: 12,
    marginTop: 12,
  },
  gpsHintText: { flex: 1, fontSize: 11.5, lineHeight: 16, marginLeft: 7 },
  navControls: { flexDirection: 'row', marginTop: 12, marginBottom: 6, columnGap: 7 },
  stepsTitle: { fontSize: 18, fontWeight: '800', marginTop: 17, marginBottom: 4 },
  startRouteButton: { marginTop: 12, marginBottom: 8, minHeight: 54 },
});

export default NavigationScreen;
