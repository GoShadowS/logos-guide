/** Bottom navigation: Home | Map | Schedule | More. */
import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { MapScreen } from '../screens/MapScreen';
import { SearchScreen } from '../screens/SearchScreen';
import { FavoritesScreen } from '../screens/FavoritesScreen';
import { ScheduleScreen } from '../screens/ScheduleScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';

const Tab = createBottomTabNavigator();

export function TabNavigator() {
  const { theme } = useTheme();
  const { t } = useI18n();
  const insets = useSafeAreaInsets();

  const iconFor = (routeName, focused) => {
    switch (routeName) {
      case 'MapTab':
        return focused ? 'map' : 'map-outline';
      case 'ScheduleTab':
        return focused ? 'calendar-month' : 'calendar-month-outline';
      case 'SettingsTab':
        return 'dots-grid';
      case 'SearchTab':
      default:
        return focused ? 'home' : 'home-outline';
    }
  };

  return (
    <Tab.Navigator
      screenOptions={({ route }) => ({
        headerShown: false,
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: theme.colors.textTertiary,
        tabBarHideOnKeyboard: true,
        tabBarStyle: {
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.border,
          borderTopWidth: 1,
          height: 68 + insets.bottom,
          paddingTop: 6,
          paddingBottom: Math.max(7, insets.bottom),
          elevation: 0,
        },
        tabBarLabelStyle: {
          fontSize: 10.5,
          fontWeight: '700',
          marginTop: -2,
        },
        tabBarIcon: ({ color, size, focused }) => (
          <MaterialCommunityIcons
            name={iconFor(route.name, focused)}
            size={size + (focused ? 1 : 0)}
            color={color}
          />
        ),
      })}
    >
      <Tab.Screen name="SearchTab" component={SearchScreen} options={{ title: t('tabs.home') }} />
      <Tab.Screen name="MapTab" component={MapScreen} options={{ title: t('tabs.map') }} />
      <Tab.Screen name="ScheduleTab" component={ScheduleScreen} options={{ title: t('tabs.schedule') }} />
      <Tab.Screen name="SettingsTab" component={SettingsScreen} options={{ title: t('tabs.more') }} />
      {/* Keep favorites reachable from More without adding a fifth visible tab. */}
      <Tab.Screen
        name="FavoritesTab"
        component={FavoritesScreen}
        options={{
          title: t('tabs.favorites'),
          tabBarButton: () => null,
        }}
      />
    </Tab.Navigator>
  );
}

export default TabNavigator;
