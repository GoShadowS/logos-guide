/**
 * components/FloorSelector.js — переключатель этажей (выпадающий список).
 * Показывает только внутренние этажи корпуса.
 */
import React, { useMemo, useState } from 'react';
import { View, Text, StyleSheet, Pressable, Modal } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';
import { getFloor, getBuildingOfFloor, getFloorsOfBuilding } from '../data';

export function FloorSelector({ floorId, onChange, style }) {
  const { theme } = useTheme();
  const { t, language } = useI18n();
  const [open, setOpen] = useState(false);

  const floor = getFloor(floorId);
  const building = getBuildingOfFloor(floorId);

  const options = useMemo(() => {
    const floors = getFloorsOfBuilding(building?.id || 'main');
    return floors.map((item) => ({
      id: item.id,
      title: t('ui.floor') + ' ' + item.level,
      level: item.level,
    }));
  }, [building, t]);

  const currentTitle = floor ? `${t('ui.floor')} ${floor.level}` : '';

  return (
    <View style={[styles.wrapper, style]}>
      <Pressable
        onPress={() => setOpen(true)}
        accessibilityRole="button"
        accessibilityLabel={t('map.chooseFloor')}
        style={({ pressed }) => [
          styles.trigger,
          {
            backgroundColor: theme.colors.surface,
            borderColor: theme.colors.border,
            opacity: pressed ? 0.85 : 1,
          },
        ]}
      >
        <Text style={[styles.triggerText, { color: theme.colors.text }]} numberOfLines={1}>
          {currentTitle}
        </Text>
        <MaterialCommunityIcons
          name={open ? 'chevron-up' : 'chevron-down'}
          size={18}
          color={theme.colors.textSecondary}
        />
      </Pressable>

      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable style={styles.backdrop} onPress={() => setOpen(false)}>
          <View
            style={[
              styles.dropdown,
              { backgroundColor: theme.colors.surfaceElevated, borderColor: theme.colors.border },
            ]}
          >
            <Text style={[styles.dropdownTitle, { color: theme.colors.textSecondary }]}>
              {t('map.chooseFloor')}
            </Text>
            {options.map((option) => {
              const active = option.id === floorId;
              return (
                <Pressable
                  key={option.id}
                  onPress={() => {
                    setOpen(false);
                    if (option.id !== floorId) onChange(option.id);
                  }}
                  accessibilityRole="button"
                  accessibilityState={{ selected: active }}
                  style={({ pressed }) => [
                    styles.option,
                    {
                      backgroundColor: active
                        ? theme.colors.primarySoft
                        : pressed
                          ? theme.colors.surfaceAlt
                          : 'transparent',
                    },
                  ]}
                >
                  <MaterialCommunityIcons
                    name="floor-plan"
                    size={18}
                    color={active ? theme.colors.primary : theme.colors.textSecondary}
                  />
                  <Text
                    style={[
                      styles.optionText,
                      { color: active ? theme.colors.primary : theme.colors.text },
                    ]}
                  >
                    {option.title}
                  </Text>
                  {active ? (
                    <MaterialCommunityIcons name="check" size={18} color={theme.colors.primary} />
                  ) : null}
                </Pressable>
              );
            })}
            {building ? (
              <Text style={[styles.buildingNote, { color: theme.colors.textTertiary }]}>
                {building.name?.[language] || building.name?.ru}
              </Text>
            ) : null}
          </View>
        </Pressable>
      </Modal>
    </View>
  );
}

/** Горизонтальные чипсы этажей (альтернативный вариант) */
export function FloorChips({ floorId, onChange }) {
  const { theme } = useTheme();
  const { t } = useI18n();
  const building = getBuildingOfFloor(floorId);
  const options = building
    ? getFloorsOfBuilding(building.id).map((item) => ({
        id: item.id,
        title: t('ui.floor') + ' ' + item.level,
      }))
    : [];

  return (
    <View style={styles.chipsRow}>
      {options.map((option) => {
        const active = option.id === floorId;
        return (
          <Pressable
            key={option.id}
            onPress={() => onChange(option.id)}
            style={[
              styles.chip,
              {
                backgroundColor: active ? theme.colors.primary : theme.colors.surface,
                borderColor: active ? theme.colors.primary : theme.colors.border,
              },
            ]}
          >
            <Text style={{ color: active ? theme.colors.primaryText : theme.colors.textSecondary, fontSize: 13, fontWeight: '600' }}>
              {option.title}
            </Text>
          </Pressable>
        );
      })}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { alignItems: 'flex-start' },
  trigger: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: 1,
    minWidth: 116,
    justifyContent: 'space-between',
  },
  triggerText: { fontSize: 14, fontWeight: '600', marginRight: 6 },
  backdrop: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.35)',
    justifyContent: 'flex-start',
    alignItems: 'flex-start',
    padding: 16,
    paddingTop: 120,
  },
  dropdown: {
    minWidth: 220,
    borderRadius: 16,
    borderWidth: 1,
    padding: 8,
  },
  dropdownTitle: {
    fontSize: 12,
    fontWeight: '600',
    paddingHorizontal: 10,
    paddingVertical: 6,
    textTransform: 'uppercase',
  },
  option: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 10,
    paddingVertical: 11,
    borderRadius: 10,
  },
  optionText: { flex: 1, fontSize: 15, fontWeight: '600', marginLeft: 10 },
  buildingNote: { fontSize: 12, paddingHorizontal: 10, paddingTop: 8, paddingBottom: 4 },
  chipsRow: { flexDirection: 'row', flexWrap: 'wrap' },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 999,
    borderWidth: 1,
    marginRight: 8,
    marginBottom: 8,
  },
});

export default FloorSelector;
