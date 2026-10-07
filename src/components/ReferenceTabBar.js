/** Bottom tab bar replica used on the reference-style room detail screen. */
import React from 'react';
import { View, Text, StyleSheet, Pressable } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';

const ITEMS = [
  { name: 'SearchTab', titleKey: 'home', icon: 'home' },
  { name: 'MapTab', titleKey: 'map', icon: 'map-outline' },
  { name: 'ScheduleTab', titleKey: 'schedule', icon: 'calendar-month-outline' },
  { name: 'SettingsTab', titleKey: 'more', icon: 'dots-grid' },
];

export function ReferenceTabBar({ navigation, active = 'SearchTab' }) {
  const { theme } = useTheme();
  const { t } = useI18n();
  const insets = useSafeAreaInsets();

  return (
    <View
      style={[
        styles.bar,
        {
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.border,
          height: 68 + insets.bottom,
          paddingBottom: Math.max(6, insets.bottom),
        },
      ]}
    >
      {ITEMS.map((item) => {
        const selected = item.name === active;
        const color = selected ? theme.colors.primary : theme.colors.textTertiary;
        const label = t('tabs.' + item.titleKey);
        return (
          <Pressable
            key={item.name}
            onPress={() => navigation.navigate('Main', { screen: item.name })}
            accessibilityRole="tab"
            accessibilityState={{ selected }}
            accessibilityLabel={label}
            style={styles.item}
          >
            <MaterialCommunityIcons name={selected && item.name === 'SearchTab' ? 'home' : item.icon} size={23} color={color} />
            <Text style={[styles.label, { color }]}>{label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  bar: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 0,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-around',
    borderTopWidth: 1,
    paddingTop: 5,
  },
  item: { flex: 1, alignItems: 'center', justifyContent: 'center', paddingTop: 2 },
  label: { fontSize: 10.5, lineHeight: 13, fontWeight: '700', marginTop: 1 },
});

export default ReferenceTabBar;
