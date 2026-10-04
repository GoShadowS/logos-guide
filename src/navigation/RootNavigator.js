/**
 * navigation/RootNavigator.js — корневая навигация (стек + вкладки).
 */
import React from 'react';
import { NavigationContainer, DefaultTheme, DarkTheme } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { TabNavigator } from './TabNavigator';
import { PoiDetailScreen } from '../screens/PoiDetailScreen';
import { NavigationScreen } from '../screens/NavigationScreen';
import { FavoritesScreen } from '../screens/FavoritesScreen';
import { AboutScreen } from '../screens/AboutScreen';
import { useTheme } from '../theme';

// Создаём навигатор стека (один раз на модуль).
const Stack = createNativeStackNavigator();

export function RootNavigator() {
  const { theme } = useTheme();

  // Тема навигации: берём стандартную (светлую/тёмную) как основу, чтобы не потерять
  // поле fonts (его использует @react-navigation/bottom-tabs), и перекрашиваем цвета.
  const base = theme.isDark ? DarkTheme : DefaultTheme;
  const navTheme = {
    ...base,
    dark: theme.isDark,
    colors: {
      ...base.colors,
      primary: theme.colors.primary,
      background: theme.colors.background,
      card: theme.colors.surface,
      text: theme.colors.text,
      border: theme.colors.border,
      notification: theme.colors.primary,
    },
  };

  return (
    <NavigationContainer theme={navTheme}>
      <Stack.Navigator
        screenOptions={{
          headerShown: false,
          contentStyle: { backgroundColor: theme.colors.background },
          animation: 'slide_from_right',
        }}
      >
        <Stack.Screen name="Main" component={TabNavigator} />
        <Stack.Screen name="PoiDetail" component={PoiDetailScreen} />
        <Stack.Screen name="Navigation" component={NavigationScreen} />
        <Stack.Screen name="About" component={AboutScreen} />
      </Stack.Navigator>
    </NavigationContainer>
  );
}

export default RootNavigator;
