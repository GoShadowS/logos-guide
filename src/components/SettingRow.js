/**
 * components/SettingRow.js — строка настроек (иконка, заголовок, значение, справа — контрол).
 */
import React from 'react';
import { View, Text, StyleSheet, Pressable, Switch } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';

export function SettingRow({ icon, title, subtitle, value, onPress, right, children, danger }) {
  const { theme } = useTheme();
  const color = danger ? theme.colors.danger : theme.colors.text;

  const content = (
    <>
      {icon ? (
        <View style={[styles.iconWrap, { backgroundColor: theme.colors.surfaceAlt }]}>
          <MaterialCommunityIcons name={icon} size={20} color={color} />
        </View>
      ) : null}
      <View style={styles.textWrap}>
        <Text style={[styles.title, { color }]}>{title}</Text>
        {subtitle ? (
          <Text style={[styles.subtitle, { color: theme.colors.textSecondary }]}>{subtitle}</Text>
        ) : null}
        {children}
      </View>
      <View style={styles.rightWrap}>
        {value ? <Text style={[styles.value, { color: theme.colors.textSecondary }]}>{value}</Text> : null}
        {right}
        {onPress && !right ? (
          <MaterialCommunityIcons name="chevron-right" size={22} color={theme.colors.textTertiary} />
        ) : null}
      </View>
    </>
  );

  if (onPress) {
    return (
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        style={({ pressed }) => [
          styles.row,
          { backgroundColor: pressed ? theme.colors.surfaceAlt : 'transparent' },
        ]}
      >
        {content}
      </Pressable>
    );
  }
  return <View style={styles.row}>{content}</View>;
}

/** Строка с переключателем (Switch) */
export function SettingSwitchRow({ icon, title, subtitle, value, onValueChange }) {
  const { theme } = useTheme();
  return (
    <SettingRow
      icon={icon}
      title={title}
      subtitle={subtitle}
      right={
        <Switch
          value={value}
          onValueChange={onValueChange}
          trackColor={{ true: theme.colors.primary, false: theme.colors.borderStrong }}
          thumbColor="#FFFFFF"
        />
      }
    />
  );
}

const styles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingVertical: 13,
    paddingHorizontal: 16,
  },
  iconWrap: {
    width: 36,
    height: 36,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 12,
  },
  textWrap: { flex: 1 },
  title: { fontSize: 15, fontWeight: '600' },
  subtitle: { fontSize: 12.5, marginTop: 2, lineHeight: 17 },
  rightWrap: { flexDirection: 'row', alignItems: 'center', marginLeft: 8 },
  value: { fontSize: 14, marginRight: 4 },
});

export default SettingRow;
