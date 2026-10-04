/**
 * navigation/TabNavigator.js — нижняя навигация приложения.
 * Вкладки: Главная | Карта | Ещё.
 */
import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MapScreen } from '../screens/MapScreen';
import { SearchScreen } from '../screens/SearchScreen';
import { FavoritesScreen } from '../screens/FavoritesScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';

const Tab = createBottomTabNavigator();

export function TabNavigator() {
  const { theme } = useTheme();
  const { t } = useI18n();
  const insets = useSafeAreaInsets();

  const iconFor = (routeName) => {
    switch (routeName) {
      case 'MapTab':
        return 'map-outline';
      case 'SearchTab':
        return 'home-outline';
      case 'SettingsTab':
        return 'dots-grid';
      case 'FavoritesTab':
        return 'heart-outline';
      default:
        return 'circle';
    }
  };

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: theme.colors.textTertiary,
        tabBarStyle: {
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.border,
          borderTopWidth: 1,
          height: 58 + insets.bottom,
          paddingTop: 4,
          paddingBottom: Math.max(6, insets.bottom),
        },
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
        },
        tabBarIcon: ({ color, size }) => (
          <MaterialCommunityIcons name={iconFor(route.name)} size={size - 2} color={color} />
        ),
      })}
    >
        <Tab.Screen name="SearchTab" component={SearchScreen} options={{ title: t('tabs.home') }} />
        <Tab.Screen name="MapTab" component={MapScreen} options={{ title: t('tabs.map') }} />
        <Tab.Screen
          name="FavoritesTab"
          component={FavoritesScreen}
          options={{ title: t('tabs.favorites') }}
        />
      <Tab.Screen
        name="SettingsTab"
        component={SettingsScreen}
        options={{ title: t('tabs.more') }}
      />
    </Tab.Navigator>
  );
}

export default TabNavigator;
