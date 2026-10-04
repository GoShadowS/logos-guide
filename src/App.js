/**
 * src/App.js — корневой компонент приложения «ЛОГОС: Путеводитель».
 *
 * Обёртки:
 *   SafeAreaProvider  — безопасные области (челки, индикаторы);
 *   ThemeProvider     — светлая/тёмная тема;
 *   I18nProvider      — локализация (русский/английский);
 *   RootNavigator     — навигация по экранам.
 */
import React, { useCallback, useEffect, useState } from 'react';
import { StatusBar } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import * as SplashScreen from 'expo-splash-screen';

import { ThemeProvider, useTheme } from './theme';
import { I18nProvider } from './localization/I18nProvider';
import { RootNavigator } from './navigation/RootNavigator';

// Не скрываем splash-экран, пока приложение не готово
SplashScreen.preventAutoHideAsync().catch(() => {});

/** Внутренний контент: знает тему и язык, поэтому может настроить StatusBar */
function AppContent({ onReady }) {
  const { theme } = useTheme();

  useEffect(() => {
    // Как только тема и язык загружены — скрываем splash
    const timer = setTimeout(() => {
      onReady();
    }, 150);
    return () => clearTimeout(timer);
  }, [onReady]);

  return (
    <>
      <StatusBar
        barStyle={theme.isDark ? 'light-content' : 'dark-content'}
        backgroundColor="transparent"
        translucent
      />
      <RootNavigator />
    </>
  );
}

export default function App() {
  const [ready, setReady] = useState(false);
  const handleReady = useCallback(() => {
    SplashScreen.hideAsync().catch(() => {});
    setReady(true);
  }, []);

  return (
    // GestureHandlerRootView обязателен для корректной работы жестов на Android
    <GestureHandlerRootView style={{ flex: 1 }}>
      <SafeAreaProvider>
        <ThemeProvider>
          <I18nProvider>
            <AppContent onReady={handleReady} />
          </I18nProvider>
        </ThemeProvider>
      </SafeAreaProvider>
    </GestureHandlerRootView>
  );
}
