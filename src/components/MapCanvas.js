/**
 * components/MapCanvas.js — интерактивный холст с планом этажа.
 *
 * План этажа — реальный SVG из assets/images/maps (Map/map1lower.svg,
 * Map/map2lower.svg), поверх него рисуются маршрут и маркеры точек.
 *
 * Жесты (react-native-gesture-handler + react-native-reanimated):
 *   • щипок — приближение/отдаление относительно точки между пальцами;
 *   • перетаскивание — сдвиг плана (с ограничением по границам);
 *   • двойной тап — быстрое приближение/возврат к исходному масштабу.
 *
 * Вся математика зума выполняется в worklet-функциях на UI-потоке, поэтому
 * значения масштаба и сдвига всегда актуальны (кнопки «+»/«−» и fitToPoints
 * работают и во время идущей анимации).
 */
import React, {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useState,
} from 'react';
import { StyleSheet, View } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Reanimated, {
  cancelAnimation,
  runOnUI,
  useAnimatedStyle,
  useSharedValue,
  withTiming,
} from 'react-native-reanimated';

import FloorOneSvg from '../../assets/images/maps/plan_main_floor_1.svg';
import FloorTwoSvg from '../../assets/images/maps/plan_main_floor_2.svg';
import { getFloor } from '../data';
import { useTheme } from '../theme';
import { PoiMarker } from './PoiMarker';
import { RoomHitArea } from './RoomHitArea';
import { RouteOverlay } from './RouteOverlay';
import { UserLocationMarker } from './UserLocationMarker';

/** Диапазон масштаба и шаг кнопок «+»/«−» */
const MIN_SCALE = 0.75;
const MAX_SCALE = 6;
const ZOOM_STEP = 1.6;
/** Отступ от краёв экрана при вписывании плана */
const CONTENT_PADDING = 12;

/** SVG-компонент плана для каждого этажа (файлы из папки Map/) */
const FLOOR_PLAN_COMPONENTS = {
  'main-1': FloorOneSvg,
  'main-2': FloorTwoSvg,
};

function clamp(value, min, max) {
  'worklet';
  return Math.min(Math.max(value, min), max);
}

/** Обычная (не-worklet) версия для кода, который выполняется на JS-потоке */
function clampJs(value, min, max) {
  return Math.min(Math.max(value, min), max);
}

export const MapCanvas = forwardRef(function MapCanvas(
  {
    floorId,
    pois = [],
    route = null,
    startPoi = null,
    endPoi = null,
    selectedPoiId = null,
    onPoiPress,
    userLocation = null,
    dimMarkers = false,
    style,
  },
  ref
) {
  const { theme } = useTheme();
  const floor = getFloor(floorId);
  const FloorSvg = FLOOR_PLAN_COMPONENTS[floorId];
  const [container, setContainer] = useState({ width: 0, height: 0 });

  // --- Общие значения (UI-поток) -------------------------------------------
  const containerW = useSharedValue(0);
  const containerH = useSharedValue(0);
  const contentW = useSharedValue(0);
  const contentH = useSharedValue(0);
  const scale = useSharedValue(1);
  const translateX = useSharedValue(0);
  const translateY = useSharedValue(0);
  const startScale = useSharedValue(1);
  const startX = useSharedValue(0);
  const startY = useSharedValue(0);
  const opacity = useSharedValue(1);

  // Плавная смена этажа: подменяем план и плавно проявляем его.
  // Важно: подмена НЕ должна зависеть от колбэка анимации — его отменяют
  // соседние эффекты (сброс масштаба при смене этажа), и этаж «залипал».
  const [displayedFloorId, setDisplayedFloorId] = useState(floorId);

  // --- Размеры контента -----------------------------------------------------
  const onLayout = useCallback(
    (event) => {
      const { width, height } = event.nativeEvent.layout;
      setContainer({ width, height });
      containerW.value = width;
      containerH.value = height;
    },
    [containerW, containerH]
  );

  /** Размер, в который план вписывается с сохранением пропорций */
  const contentSize = useMemo(() => {
    if (!floor || container.width === 0 || container.height === 0) {
      return { width: 0, height: 0 };
    }
    const aspect = floor.width / floor.height;
    const maxWidth = Math.max(0, container.width - CONTENT_PADDING * 2);
    const maxHeight = Math.max(0, container.height - CONTENT_PADDING * 2);
    if (maxWidth / maxHeight > aspect) {
      return { width: maxHeight * aspect, height: maxHeight };
    }
    return { width: maxWidth, height: maxWidth / aspect };
  }, [floor, container]);

  useEffect(() => {
    contentW.value = contentSize.width;
    contentH.value = contentSize.height;
  }, [contentSize, contentW, contentH]);

  // --- Worklet-математика зума ---------------------------------------------

  /** Предельный сдвиг, при котором план не «уезжает» за пределы экрана */
  const maxShift = useCallback((targetScale) => {
    'worklet';
    return {
      x: Math.max(0, (contentW.value * targetScale - containerW.value) / 2),
      y: Math.max(0, (contentH.value * targetScale - containerH.value) / 2),
    };
  }, []);

  /**
   * Устанавливает масштаб, сохраняя под курсором/пальцем ту же точку плана.
   * @param {number} targetScale — желаемый масштаб
   * @param {number} focalX — координата фокуса в системе контейнера
   * @param {number} focalY
   * @param {boolean} animate — анимировать переход
   */
  const applyZoom = useCallback(
    (targetScale, focalX, focalY, animate) => {
      'worklet';
      const width = containerW.value;
      const height = containerH.value;
      if (!contentW.value || !contentH.value || !width || !height) return;

      const next = clamp(targetScale, MIN_SCALE, MAX_SCALE);
      const ratio = next / scale.value;
      const focusX = focalX - width / 2;
      const focusY = focalY - height / 2;
      const limit = maxShift(next);
      const nextX = clamp(focusX - (focusX - translateX.value) * ratio, -limit.x, limit.x);
      const nextY = clamp(focusY - (focusY - translateY.value) * ratio, -limit.y, limit.y);

      if (animate) {
        translateX.value = withTiming(nextX, { duration: 200 });
        translateY.value = withTiming(nextY, { duration: 200 });
        scale.value = withTiming(next, { duration: 200 });
      } else {
        translateX.value = nextX;
        translateY.value = nextY;
        scale.value = next;
      }
    },
    [maxShift, scale, translateX, translateY]
  );

  /** Приближение/отдаление в `factor` раз относительно точки (focalX, focalY) */
  const zoomBy = useCallback(
    (factor, focalX, focalY) => {
      'worklet';
      const cx = focalX == null ? containerW.value / 2 : focalX;
      const cy = focalY == null ? containerH.value / 2 : focalY;
      applyZoom(scale.value * factor, cx, cy, true);
    },
    [applyZoom, scale]
  );

  /** Сброс к исходному виду (масштаб 1, без сдвига) */
  const resetView = useCallback(() => {
    'worklet';
    cancelAnimation(scale);
    cancelAnimation(translateX);
    cancelAnimation(translateY);
    translateX.value = withTiming(0, { duration: 200 });
    translateY.value = withTiming(0, { duration: 200 });
    scale.value = withTiming(1, { duration: 200 });
  }, [scale, translateX, translateY]);

  /** Подгоняет вид под набор точек плана (в координатах viewBox этажа) */
  const fitToPointsWorklet = useCallback(
    (floorWidth, floorHeight, points) => {
      'worklet';
      if (!points || points.length === 0) return;
      const width = containerW.value;
      const height = containerH.value;
      const cw = contentW.value;
      const ch = contentH.value;
      if (!width || !height || !cw || !ch || !floorWidth || !floorHeight) return;

      let minX = points[0].x;
      let maxX = points[0].x;
      let minY = points[0].y;
      let maxY = points[0].y;
      for (let i = 1; i < points.length; i += 1) {
        const point = points[i];
        minX = Math.min(minX, point.x);
        maxX = Math.max(maxX, point.x);
        minY = Math.min(minY, point.y);
        maxY = Math.max(maxY, point.y);
      }

      // Запас по краям, чтобы маршрут не упирался в границы экрана
      const padding = Math.max(100, Math.min(floorWidth, floorHeight) * 0.18);
      const boundsWidth = Math.max(1, maxX - minX + padding * 2);
      const boundsHeight = Math.max(1, maxY - minY + padding * 2);
      const targetScale = clamp(
        Math.min(
          (width - 24) / (boundsWidth * (cw / floorWidth)),
          (height - 24) / (boundsHeight * (ch / floorHeight))
        ),
        MIN_SCALE,
        MAX_SCALE
      );

      const centerX = ((minX + maxX) / 2 / floorWidth) * cw;
      const centerY = ((minY + maxY) / 2 / floorHeight) * ch;
      const limitX = Math.max(0, (cw * targetScale - width) / 2);
      const limitY = Math.max(0, (ch * targetScale - height) / 2);

      translateX.value = withTiming(clamp(width / 2 - centerX * targetScale, -limitX, limitX), {
        duration: 280,
      });
      translateY.value = withTiming(clamp(height / 2 - centerY * targetScale, -limitY, limitY), {
        duration: 280,
      });
      scale.value = withTiming(targetScale, { duration: 280 });
    },
    [scale, translateX, translateY]
  );

  // --- Жесты ----------------------------------------------------------------
  const pinchGesture = Gesture.Pinch()
    .onStart(() => {
      cancelAnimation(scale);
      cancelAnimation(translateX);
      cancelAnimation(translateY);
      startScale.value = scale.value;
      startX.value = translateX.value;
      startY.value = translateY.value;
    })
    .onUpdate((event) => {
      const next = clamp(startScale.value * event.scale, MIN_SCALE, MAX_SCALE);
      const ratio = next / startScale.value;
      const limit = maxShift(next);
      const focusX = event.focalX - containerW.value / 2;
      const focusY = event.focalY - containerH.value / 2;
      scale.value = next;
      translateX.value = clamp(focusX - (focusX - startX.value) * ratio, -limit.x, limit.x);
      translateY.value = clamp(focusY - (focusY - startY.value) * ratio, -limit.y, limit.y);
    });

  const panGesture = Gesture.Pan()
    .maxPointers(1)
    .minDistance(8)
    .onStart(() => {
      cancelAnimation(translateX);
      cancelAnimation(translateY);
      startX.value = translateX.value;
      startY.value = translateY.value;
    })
    .onUpdate((event) => {
      const limit = maxShift(scale.value);
      translateX.value = clamp(startX.value + event.translationX, -limit.x, limit.x);
      translateY.value = clamp(startY.value + event.translationY, -limit.y, limit.y);
    });

  const doubleTapGesture = Gesture.Tap()
    .numberOfTaps(2)
    .maxDuration(250)
    .onEnd((event) => {
      zoomBy(scale.value > 1.8 ? 1 / scale.value : ZOOM_STEP, event.x, event.y);
    });

  const composedGesture = Gesture.Simultaneous(doubleTapGesture, panGesture, pinchGesture);

  // --- Публичный API (для кнопок зума на экране) ---------------------------
  useImperativeHandle(
    ref,
    () => ({
      zoomIn: () => runOnUI(zoomBy)(ZOOM_STEP),
      zoomOut: () => runOnUI(zoomBy)(1 / ZOOM_STEP),
      resetView: () => runOnUI(resetView)(),
      fitToPoints: (points) => {
        const onFloor = (points || []).filter((point) => point && point.floorId === floorId);
        if (!onFloor.length || !floor) return;
        runOnUI(fitToPointsWorklet)(
          floor.width,
          floor.height,
          onFloor.map((point) => ({ x: point.x, y: point.y }))
        );
      },
    }),
    [zoomBy, resetView, fitToPointsWorklet, floor, floorId]
  );

  // --- Реакция на смену этажа ----------------------------------------------
  useEffect(() => {
    if (floorId === displayedFloorId) return;
    setDisplayedFloorId(floorId);
    opacity.value = 0;
    opacity.value = withTiming(1, { duration: 180 });
  }, [floorId, displayedFloorId, opacity]);

  useEffect(() => {
    cancelAnimation(scale);
    cancelAnimation(translateX);
    cancelAnimation(translateY);
    scale.value = 1;
    translateX.value = 0;
    translateY.value = 0;
  }, [floorId, scale, translateX, translateY]);

  // При изменении размеров экрана/плана возвращаем вид в допустимые границы
  useEffect(() => {
    const limitX = Math.max(0, (contentSize.width - container.width) / 2);
    const limitY = Math.max(0, (contentSize.height - container.height) / 2);
    translateX.value = clampJs(translateX.value, -limitX, limitX);
    translateY.value = clampJs(translateY.value, -limitY, limitY);
  }, [contentSize, container, translateX, translateY]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [
      { translateX: translateX.value },
      { translateY: translateY.value },
      { scale: scale.value },
    ],
  }));

  // --- Данные отображаемого этажа ------------------------------------------
  const displayedFloor = getFloor(displayedFloorId) || floor;
  const DisplayedFloorSvg = FLOOR_PLAN_COMPONENTS[displayedFloor?.id];

  const routeEndIds = useMemo(() => {
    const ids = new Set();
    if (startPoi) ids.add(startPoi.id);
    if (endPoi) ids.add(endPoi.id);
    return ids;
  }, [startPoi, endPoi]);

  const visiblePois = useMemo(
    () => pois.filter((poi) => poi.floorId === displayedFloor?.id),
    [pois, displayedFloor?.id]
  );

  const routePointsOnFloor = useMemo(
    () => (route ? route.points.filter((point) => point.floorId === displayedFloor?.id) : []),
    [route, displayedFloor?.id]
  );

  if (!floor || !displayedFloor) {
    return <View style={[styles.container, { backgroundColor: theme.colors.background }, style]} onLayout={onLayout} />;
  }

  return (
    <View
      style={[styles.container, { backgroundColor: theme.colors.background }, style]}
      onLayout={onLayout}
    >
      <GestureDetector gesture={composedGesture}>
        <Reanimated.View style={styles.gestureArea}>
          <Reanimated.View
            style={[
              styles.content,
              {
                width: contentSize.width,
                height: contentSize.height,
                marginLeft: -contentSize.width / 2,
                marginTop: -contentSize.height / 2,
              },
              animatedStyle,
            ]}
          >
            {/* Реальный план этажа (SVG из папки Map/) */}
            {DisplayedFloorSvg ? (
              <DisplayedFloorSvg width={contentSize.width} height={contentSize.height} />
            ) : null}

            {/* В тёмной теме план дополнительно затемняется */}
            {theme.colors.mapOverlay ? (
              <View style={[styles.overlay, { backgroundColor: theme.colors.mapOverlay }]} pointerEvents="none" />
            ) : null}

            {routePointsOnFloor.length > 1 ? (
              <RouteOverlay
                floor={displayedFloor}
                theme={theme}
                points={routePointsOnFloor}
                startColor={startPoi ? theme.colors.mapStart : undefined}
                endColor={endPoi ? theme.colors.mapEnd : undefined}
              />
            ) : null}

            {visiblePois.map((poi) =>
              poi.kind === 'room' ? (
                <RoomHitArea
                  key={poi.id}
                  poi={poi}
                  floor={displayedFloor}
                  selected={poi.id === selectedPoiId}
                  onPress={onPoiPress}
                  dim={dimMarkers && poi.id !== selectedPoiId}
                />
              ) : (
                <PoiMarker
                  key={poi.id}
                  poi={poi}
                  floor={displayedFloor}
                  selected={poi.id === selectedPoiId}
                  showLabel
                  onPress={onPoiPress}
                  isRouteEndpoint={routeEndIds.has(poi.id)}
                  size={dimMarkers ? 28 : 34}
                />
              )
            )}

            <UserLocationMarker floor={displayedFloor} location={userLocation} />
          </Reanimated.View>
        </Reanimated.View>
      </GestureDetector>
    </View>
  );
});

const styles = StyleSheet.create({
  container: { flex: 1, overflow: 'hidden' },
  gestureArea: { flex: 1 },
  content: { position: 'absolute', left: '50%', top: '50%' },
  overlay: { ...StyleSheet.absoluteFillObject },
});

export default MapCanvas;
