/**
 * components/BottomSheet.js — нижняя панель (bottom sheet) с возможностью
 * свернуть/развернуть перетаскиванием.
 *
 * Используется на карте для показа информации о выбранной точке и маршрута.
 */
import React, { useCallback, useRef } from 'react';
import { View, StyleSheet, Animated, Pressable, useWindowDimensions } from 'react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import Reanimated, { runOnJS } from 'react-native-reanimated';
import { useTheme } from '../theme';

/** Высота свёрнутой панели (экраны рассчитывают от неё расположение кнопок) */
export const SHEET_COLLAPSED_HEIGHT = 92;
/** Доля экрана, которую занимает развёрнутая панель */
export const SHEET_EXPANDED_RATIO = 0.62;

/** Фактическая высота панели — для размещения плавающих элементов над ней */
export function getSheetHeight(collapsed, screenHeight) {
  return collapsed ? SHEET_COLLAPSED_HEIGHT : Math.round(screenHeight * SHEET_EXPANDED_RATIO);
}

export function BottomSheet({ children, collapsed, onToggle, style }) {
  const { theme } = useTheme();
  const { height: screenHeight } = useWindowDimensions();
  const height = getSheetHeight(collapsed, screenHeight);
  const animatedHeight = useRef(new Animated.Value(height)).current;

  React.useEffect(() => {
    Animated.spring(animatedHeight, {
      toValue: height,
      useNativeDriver: false,
      damping: 20,
      stiffness: 180,
      mass: 0.9,
    }).start();
  }, [height, animatedHeight]);

  return (
    <Animated.View
      style={[
        styles.sheet,
        {
          height: animatedHeight,
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.border,
        },
        style,
      ]}
    >
      <SheetHandle collapsed={collapsed} onToggle={onToggle} />
      {children}
    </Animated.View>
  );
}

/** «Ручка» панели: тап или перетаскивание меняет состояние */
function SheetHandle({ collapsed, onToggle }) {
  const { theme } = useTheme();

  const notify = useCallback(() => {
    if (onToggle) onToggle();
  }, [onToggle]);

  const gesture = Gesture.Pan()
    .onEnd((event) => {
      const shouldToggle = collapsed ? event.translationY < -60 : event.translationY > 60;
      if (shouldToggle && onToggle) runOnJS(notify)();
    });

  return (
    <GestureDetector gesture={gesture}>
      <Reanimated.View style={styles.handleArea}>
        <Pressable onPress={notify} accessibilityRole="button" hitSlop={8}>
          <View style={[styles.handle, { backgroundColor: theme.colors.borderStrong }]} />
        </Pressable>
      </Reanimated.View>
    </GestureDetector>
  );
}

const styles = StyleSheet.create({
  sheet: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    borderTopLeftRadius: 22,
    borderTopRightRadius: 22,
    borderTopWidth: 1,
    shadowColor: '#000',
    shadowOpacity: 0.12,
    shadowRadius: 16,
    shadowOffset: { width: 0, height: -4 },
    elevation: 12,
  },
  handleArea: {
    alignItems: 'center',
    paddingTop: 8,
    paddingBottom: 4,
  },
  handle: {
    width: 44,
    height: 5,
    borderRadius: 3,
  },
});

export default BottomSheet;
