/**
 * screens/FavoritesScreen.js — избранные места.
 */
import React, { useCallback, useMemo } from 'react';
import { View, Text, StyleSheet, ScrollView, Pressable, Alert } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { EmptyState } from '../components/EmptyState';
import { PoiIcon } from '../components/PoiIcon';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { useFavorites } from '../hooks/useFavorites';
import { getPoi, getFloor, getBuildingOfFloor, getDefaultStartPoint } from '../data';
import { buildRoute } from '../services/pathfinding';
import { formatFloorAndBuilding } from '../utils/format';

export function FavoritesScreen({ navigation }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const insets = useSafeAreaInsets();
  const { ids, toggle } = useFavorites();

  const favorites = useMemo(
    () => ids.map((id) => getPoi(id)).filter(Boolean),
    [ids]
  );

  const handleRoute = useCallback(
    (poi) => {
      const start = getDefaultStartPoint();
      if (!start) {
        Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
        return;
      }
      const result = buildRoute(start, { floorId: poi.floorId, x: poi.x, y: poi.y });
      if (!result.ok) {
        Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
        return;
      }
      navigation.navigate('Navigation', {
        routeData: result,
        startPoi: { id: 'start', type: 'entrance', floorId: start.floorId, x: start.x, y: start.y },
        endPoi: poi,
      });
    },
    [navigation, t]
  );

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <View style={[styles.header, { paddingTop: insets.top + 12 }]}>
        <Text style={[styles.title, { color: theme.colors.text }]}>{t('favorites.title')}</Text>
      </View>

      <ScrollView contentContainerStyle={styles.scroll}>
        {favorites.length === 0 ? (
          <EmptyState
            icon="heart-outline"
            title={t('favorites.empty')}
            hint={t('favorites.emptyHint')}
          />
        ) : (
          favorites.map((poi) => {
            const floor = getFloor(poi.floorId);
            const building = getBuildingOfFloor(poi.floorId);
            return (
              <Card key={poi.id} style={styles.card} padded={false}>
                <Pressable
                  onPress={() => navigation.navigate('PoiDetail', { poiId: poi.id })}
                  style={styles.row}
                >
                  <PoiIcon type={poi.type} size={44} />
                  <View style={styles.text}>
                    <Text style={[styles.itemTitle, { color: theme.colors.text }]} numberOfLines={1}>
                      {poi.number || poi.name?.[language] || poi.name?.ru}
                    </Text>
                    <Text style={[styles.itemSubtitle, { color: theme.colors.textSecondary }]} numberOfLines={1}>
                      {formatFloorAndBuilding(floor, building, t, language)}
                    </Text>
                  </View>
                  <MaterialCommunityIcons name="chevron-right" size={22} color={theme.colors.textTertiary} />
                </Pressable>
                <View style={styles.actions}>
                  <Button
                    title={t('ui.buildRoute')}
                    icon="navigation-variant"
                    size="sm"
                    onPress={() => handleRoute(poi)}
                    style={{ flex: 1 }}
                  />
                  <Button
                    title={t('favorites.remove')}
                    icon="heart-broken"
                    variant="secondary"
                    size="sm"
                    onPress={() => toggle(poi.id)}
                  />
                </View>
              </Card>
            );
          })
        )}
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { paddingHorizontal: 16, paddingBottom: 8 },
  title: { fontSize: 26, fontWeight: '800' },
  scroll: { padding: 12, paddingBottom: 40 },
  card: { marginBottom: 10 },
  row: { flexDirection: 'row', alignItems: 'center', padding: 12 },
  text: { flex: 1, marginLeft: 12 },
  itemTitle: { fontSize: 16, fontWeight: '700' },
  itemSubtitle: { fontSize: 12.5, marginTop: 2 },
  actions: { flexDirection: 'row', paddingHorizontal: 12, paddingBottom: 12 },
});

export default FavoritesScreen;
