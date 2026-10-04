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
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { ScreenHeader } from '../components/ScreenHeader';
import { MapCanvas } from '../components/MapCanvas';
import { Button } from '../components/Button';
import { PoiIcon } from '../components/PoiIcon';
import { RouteStepItem, stepText } from '../components/RouteStepItem';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { getAllPois, getFloor, getBuildingOfFloor } from '../data';
import { formatDistance, formatDuration, roundMeters } from '../utils/format';

export function NavigationScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const insets = useSafeAreaInsets();
  const mapRef = useRef(null);

  const routeData = route.params?.routeData;
  const startPoi = route.params?.startPoi;
  const endPoi = route.params?.endPoi;

  const [floorId, setFloorId] = useState(routeData?.floors?.[0] || endPoi?.floorId || 'main-1');
  const [navigating, setNavigating] = useState(false);
  const [currentStep, setCurrentStep] = useState(0);

  const steps = routeData?.steps || [];
  const pois = useMemo(() => getAllPois(), []);

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

  // --- Автоматическое продвижение по шагам в режиме навигации --------------
  useEffect(() => {
    if (!navigating) return;
    const step = steps[currentStep];
    if (!step) return;
    // Время на шаг: расстояние / скорость 1.3 м/с, минимум 2.5 с
    const seconds = Math.max(2.5, step.distanceM / 1.3);
    const timer = setTimeout(() => {
      setCurrentStep((prev) => {
        if (prev + 1 >= steps.length) {
          setNavigating(false);
          return prev;
        }
        return prev + 1;
      });
    }, seconds * 1000);
    return () => clearTimeout(timer);
  }, [navigating, currentStep, steps]);

  const goNext = useCallback(() => {
    setCurrentStep((prev) => Math.min(prev + 1, steps.length - 1));
  }, [steps.length]);

  const goPrev = useCallback(() => {
    setCurrentStep((prev) => Math.max(prev - 1, 0));
  }, []);

  const stopNavigation = useCallback(() => {
    setNavigating(false);
    setCurrentStep(0);
  }, []);

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
          dimMarkers
        />

        {/* Верхняя панель: назад + этаж */}
        <View style={[styles.mapTopBar, { paddingTop: insets.top + 8 }]} pointerEvents="box-none">
          <Pressable
            onPress={() => navigation.goBack()}
            accessibilityRole="button"
            accessibilityLabel={t('ui.back')}
            style={[styles.circleButton, { backgroundColor: theme.colors.surface }]}
          >
            <MaterialCommunityIcons name="arrow-left" size={22} color={theme.colors.text} />
          </Pressable>

          <View style={[styles.floorBadge, { backgroundColor: theme.colors.surface }]}>
            <MaterialCommunityIcons name="floor-plan" size={15} color={theme.colors.primary} />
            <Text style={[styles.floorBadgeText, { color: theme.colors.text }]}>
              {`${t('ui.floor')} ${getFloor(floorId)?.level ?? ''}`}
            </Text>
          </View>

          <Pressable
            onPress={() => mapRef.current?.fitToPoints(routeData.points)}
            accessibilityRole="button"
            accessibilityLabel={t('map.resetView')}
            style={[styles.circleButton, { backgroundColor: theme.colors.surface }]}
          >
            <MaterialCommunityIcons name="fit-to-page-outline" size={20} color={theme.colors.text} />
          </Pressable>
        </View>

        {/* Пошаговый режим: крупная подсказка */}
        {navigating && currentStepData ? (
          <View style={[styles.stepBanner, { backgroundColor: theme.colors.primary }]} pointerEvents="box-none">
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
            <PoiIcon type={endPoi.type} size={44} />
            <View style={{ flex: 1, marginLeft: 12 }}>
              <Text style={[styles.destinationTitle, { color: theme.colors.text }]} numberOfLines={1}>
                {endPoi.number || endPoi.name?.[language] || endPoi.name?.ru}
              </Text>
              <Text style={[styles.destinationSubtitle, { color: theme.colors.textSecondary }]} numberOfLines={1}>
                {getBuildingOfFloor(endPoi.floorId)?.name?.[language] || t('ui.floor')}
              </Text>
            </View>
            <View style={styles.metrics}>
              <View style={styles.metricItem}>
                <MaterialCommunityIcons name="walk" size={16} color={theme.colors.primary} />
                <Text style={[styles.metricValue, { color: theme.colors.text }]}>
                  {formatDuration(routeData.durationSec, t)}
                </Text>
              </View>
              <View style={styles.metricItem}>
                <MaterialCommunityIcons name="map-marker-distance" size={16} color={theme.colors.textSecondary} />
                <Text style={[styles.metricValue, { color: theme.colors.textSecondary }]}>
                  {formatDistance(routeData.distanceM, t)}
                </Text>
              </View>
            </View>
          </View>

          {/* Пошаговая навигация */}
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
          ) : (
            <Button
              title={t('ui.startRoute')}
              icon="navigation-variant"
              size="lg"
              fullWidth
              onPress={() => {
                setCurrentStep(0);
                setNavigating(true);
              }}
              style={{ marginTop: 4 }}
            />
          )}

          {/* Список шагов */}
          <Text style={[styles.stepsTitle, { color: theme.colors.text }]}>{t('ui.steps')}</Text>
          {steps.map((step, index) => (
            <RouteStepItem
              key={index}
              step={step}
              index={index}
              active={navigating && index === currentStep}
              compact
            />
          ))}
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
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 12,
  },
  circleButton: {
    width: 38,
    height: 38,
    borderRadius: 19,
    alignItems: 'center',
    justifyContent: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.1,
    shadowRadius: 6,
    shadowOffset: { width: 0, height: 2 },
    elevation: 3,
  },
  floorBadge: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
  },
  floorBadgeText: { fontSize: 13, fontWeight: '700', marginLeft: 5 },
  stepBanner: {
    position: 'absolute',
    top: 96,
    left: 16,
    right: 16,
    borderRadius: 18,
    padding: 16,
    alignItems: 'center',
    shadowColor: '#000',
    shadowOpacity: 0.2,
    shadowRadius: 12,
    shadowOffset: { width: 0, height: 6 },
    elevation: 8,
  },
  stepBannerText: { color: '#FFFFFF', fontSize: 19, fontWeight: '800', textAlign: 'center' },
  stepBannerDistance: { color: 'rgba(255,255,255,0.85)', fontSize: 14, marginTop: 4, fontWeight: '600' },
  panel: {
    height: '46%',
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    borderTopWidth: 1,
  },
  panelHandleArea: { alignItems: 'center', paddingTop: 8 },
  panelHandle: { width: 44, height: 5, borderRadius: 3 },
  panelScroll: { padding: 16, paddingBottom: 32 },
  destinationRow: { flexDirection: 'row', alignItems: 'center' },
  destinationTitle: { fontSize: 19, fontWeight: '700' },
  destinationSubtitle: { fontSize: 13, marginTop: 2 },
  metrics: { alignItems: 'flex-end' },
  metricItem: { flexDirection: 'row', alignItems: 'center', marginBottom: 3 },
  metricValue: { fontSize: 13, fontWeight: '700', marginLeft: 4 },
  navControls: { flexDirection: 'row', marginTop: 14, marginBottom: 6 },
  stepsTitle: { fontSize: 16, fontWeight: '700', marginTop: 18, marginBottom: 6 },
});

export default NavigationScreen;
