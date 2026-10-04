/**
 * screens/PoiDetailScreen.js — карточка точки интереса (аудитории, места).
 *
 * Показывает описание, этаж, корпус, преподавателя и предлагает варианты
 * отправления для построения маршрута.
 */
import React, { useCallback, useMemo } from 'react';
import {
  View,
  Text,
  StyleSheet,
  ScrollView,
  Pressable,
  Alert,
} from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';

import { ScreenHeader } from '../components/ScreenHeader';
import { Card } from '../components/Card';
import { Button } from '../components/Button';
import { PoiIcon } from '../components/PoiIcon';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { useFavorites } from '../hooks/useFavorites';
import {
  getPoi,
  getFloor,
  getBuildingOfFloor,
  getTeacher,
  getAllPois,
  getDefaultStartPoint,
} from '../data';
import { buildRoute } from '../services/pathfinding';

export function PoiDetailScreen({ navigation, route }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const { isFavorite, toggle } = useFavorites();

  const poi = getPoi(route.params?.poiId);
  const floor = poi ? getFloor(poi.floorId) : null;
  const building = poi ? getBuildingOfFloor(poi.floorId) : null;
  const teacher = poi?.teacherId ? getTeacher(poi.teacherId) : null;

  const favorite = poi ? isFavorite(poi.id) : false;

  /** Варианты точек отправления */
  const startOptions = useMemo(() => {
    const options = [];
    const entrance = getDefaultStartPoint();
    if (entrance) {
      options.push({
        id: 'entrance',
        label: t('poi.routeFromMainEntrance'),
        point: { floorId: entrance.floorId, x: entrance.x, y: entrance.y },
      });
    }
    const cafeteria = getAllPois().find((item) => item.type === 'cafeteria');
    if (cafeteria) {
      options.push({
        id: 'cafeteria',
        label: t('poi.routeFromCafeteria'),
        point: { floorId: cafeteria.floorId, x: cafeteria.x, y: cafeteria.y },
      });
    }
    return options;
  }, [t]);

  const handleBuildRoute = useCallback(
    (startPoint) => {
      if (!poi) return;
      const result = buildRoute(startPoint, {
        floorId: poi.floorId,
        x: poi.x,
        y: poi.y,
      });
      if (!result.ok) {
        Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
        return;
      }
      navigation.navigate('Navigation', {
        routeData: result,
        startPoi: {
          id: 'start',
          type: 'entrance',
          floorId: startPoint.floorId,
          x: startPoint.x,
          y: startPoint.y,
        },
        endPoi: poi,
      });
    },
    [poi, navigation, t]
  );

  if (!poi) {
    return (
      <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
        <ScreenHeader title={t('poi.notFound')} onBack={() => navigation.goBack()} />
      </View>
    );
  }

  const title = poi.number || poi.name?.[language] || poi.name?.ru;

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      <ScreenHeader
        title={title}
        subtitle={building ? building.name?.[language] || building.name?.ru : t('ui.floor')}
        onBack={() => navigation.goBack()}
        right={
          <Pressable
            onPress={async () => toggle(poi.id)}
            hitSlop={10}
            accessibilityRole="button"
            accessibilityLabel={favorite ? t('poi.removeFromFavorites') : t('poi.addToFavorites')}
          >
            <MaterialCommunityIcons name={favorite ? 'heart' : 'heart-outline'} size={24} color={theme.colors.primary} />
          </Pressable>
        }
      />

      <ScrollView contentContainerStyle={styles.scroll}>
        {/* Шапка с иконкой */}
        <Card style={styles.heroCard}>
          <View style={styles.heroRow}>
            <PoiIcon type={poi.type} size={58} />
            <View style={{ flex: 1, marginLeft: 14 }}>
              <Text style={[styles.heroTitle, { color: theme.colors.text }]}>{title}</Text>
              <Text style={[styles.heroType, { color: theme.colors.textSecondary }]}>
                {t('poiType.' + poi.type)}
              </Text>
            </View>
          </View>

          <View style={styles.metaRow}>
            <View style={[styles.metaChip, { backgroundColor: theme.colors.surfaceAlt }]}>
              <MaterialCommunityIcons name="office-building" size={15} color={theme.colors.textSecondary} />
              <Text style={[styles.metaChipText, { color: theme.colors.textSecondary }]}>
                {building ? building.name?.[language] || building.name?.ru : t('ui.floor')}
              </Text>
            </View>
            {floor ? (
              <View style={[styles.metaChip, { backgroundColor: theme.colors.surfaceAlt }]}>
                <MaterialCommunityIcons name="floor-plan" size={15} color={theme.colors.textSecondary} />
                <Text style={[styles.metaChipText, { color: theme.colors.textSecondary }]}>
                  {t('ui.floor')} {floor.level}
                </Text>
              </View>
            ) : null}
          </View>
        </Card>

        {/* Преподаватель */}
        {teacher ? (
          <Card style={styles.card}>
            <Text style={[styles.cardTitle, { color: theme.colors.text }]}>{t('poi.teacher')}</Text>
            <View style={styles.teacherRow}>
              <PoiIcon type="office" size={40} />
              <View style={{ marginLeft: 12, flex: 1 }}>
                <Text style={[styles.teacherName, { color: theme.colors.text }]}>
                  {teacher.name?.[language] || teacher.name?.ru}
                </Text>
                <Text style={[styles.teacherSubject, { color: theme.colors.textSecondary }]}>
                  {teacher.subject?.[language] || teacher.subject?.ru}
                </Text>
              </View>
            </View>
          </Card>
        ) : null}

        {/* Описание */}
        <Card style={styles.card}>
          <Text style={[styles.cardTitle, { color: theme.colors.text }]}>{t('poi.details')}</Text>
          <Text style={[styles.description, { color: theme.colors.textSecondary }]}>
            {poi.description?.[language] || poi.description?.ru || t('poi.noDescription')}
          </Text>
        </Card>

        {/* Как добраться */}
        <Card style={styles.card}>
          <Text style={[styles.cardTitle, { color: theme.colors.text }]}>{t('poi.howToGet')}</Text>

          {startOptions.map((option) => (
            <Button
              key={option.id}
              title={option.label}
              icon="navigation-variant"
              variant="outline"
              fullWidth
              onPress={() => handleBuildRoute(option.point)}
              style={{ marginBottom: 8 }}
            />
          ))}
        </Card>

        {/* Кнопка «Построить маршрут» закреплена снизу */}
        <Button
          title={t('ui.buildRoute')}
          icon="navigation-variant"
          size="lg"
          fullWidth
          onPress={() => {
            const fallback = getDefaultStartPoint();
            if (!startOptions[0] && !fallback) {
              Alert.alert(t('ui.routeNotFound'), t('ui.routeNotFoundHint'));
              return;
            }
            handleBuildRoute(startOptions[0]?.point || fallback);
          }}
          style={{ marginTop: 4 }}
        />
      </ScrollView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1 },
  scroll: { padding: 12, paddingBottom: 40 },
  heroCard: { marginBottom: 12 },
  heroRow: { flexDirection: 'row', alignItems: 'center' },
  heroTitle: { fontSize: 22, fontWeight: '700' },
  heroType: { fontSize: 13.5, marginTop: 2 },
  metaRow: { flexDirection: 'row', marginTop: 14, flexWrap: 'wrap' },
  metaChip: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 6,
    borderRadius: 999,
    marginRight: 8,
    marginBottom: 6,
  },
  metaChipText: { fontSize: 12.5, fontWeight: '600', marginLeft: 5 },
  card: { marginBottom: 12 },
  cardTitle: { fontSize: 16, fontWeight: '700', marginBottom: 10 },
  description: { fontSize: 14.5, lineHeight: 21 },
  teacherRow: { flexDirection: 'row', alignItems: 'center' },
  teacherName: { fontSize: 15.5, fontWeight: '700' },
  teacherSubject: { fontSize: 13, marginTop: 2 },
});

export default PoiDetailScreen;
