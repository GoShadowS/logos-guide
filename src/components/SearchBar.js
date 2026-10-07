/**
 * components/SearchBar.js — строка поиска: иконка, поле ввода, кнопки очистки
 * и голосового ввода.
 *
 * Два режима:
 *   • обычное поле (value + onChangeText);
 *   • «кнопка» (onPress + editable=false) — тап открывает экран поиска.
 */
import React, { useCallback, forwardRef, useImperativeHandle, useRef } from 'react';
import { View, Text, TextInput, StyleSheet, Pressable, Platform } from 'react-native';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import { useTheme } from '../theme';
import { useI18n } from '../localization/I18nProvider';

export const SearchBar = forwardRef(function SearchBar(
  {
    value,
    onChangeText,
    onSubmit,
    onVoicePress,
    voiceActive = false,
    voiceDisabled = false,
    placeholder,
    autoFocus,
    style,
    editable = true,
    onFocus,
    onPress,
  },
  ref
) {
  const { theme } = useTheme();
  const { t } = useI18n();
  const inputRef = useRef(null);

  // Пробрасываем наружу focus/blur — экраны открывают клавиатуру при переходе
  useImperativeHandle(
    ref,
    () => ({
      focus: () => inputRef.current?.focus(),
      blur: () => inputRef.current?.blur(),
      clear: () => {
        if (onChangeText) onChangeText('');
        inputRef.current?.focus();
      },
    }),
    [onChangeText]
  );

  const containerStyle = [
    styles.container,
    {
      backgroundColor: theme.colors.surfaceAlt,
      borderColor: theme.colors.surfaceAlt,
      borderRadius: theme.radius.lg,
    },
    style,
  ];

  const handleClear = useCallback(() => {
    if (onChangeText) onChangeText('');
    inputRef.current?.focus();
  }, [onChangeText]);

  // Режим «кнопки»: поле не редактируется, тап открывает экран поиска
  if (onPress && !editable) {
    return (
      <Pressable
        onPress={onPress}
        accessibilityRole="button"
        accessibilityLabel={placeholder}
        style={({ pressed }) => [
          styles.buttonMode,
          ...containerStyle,
          pressed && { opacity: 0.85 },
        ]}
      >
        <MaterialCommunityIcons
          name="magnify"
          size={21}
          color={theme.colors.primary}
          style={styles.leftIcon}
        />
        <Text numberOfLines={1} style={[styles.placeholder, { color: theme.colors.textTertiary }]}>
          {placeholder}
        </Text>
      </Pressable>
    );
  }

  return (
    <View style={containerStyle}>
      <MaterialCommunityIcons
        name="magnify"
        size={21}
        color={theme.colors.primary}
        style={styles.leftIcon}
      />
      <TextInput
        ref={inputRef}
        value={value}
        onChangeText={onChangeText}
        onSubmitEditing={onSubmit}
        onFocus={onFocus}
        placeholder={placeholder || t('search.placeholder')}
        placeholderTextColor={theme.colors.textTertiary}
        style={[styles.input, { color: theme.colors.text }]}
        autoFocus={autoFocus}
        editable={editable}
        returnKeyType="search"
        autoCorrect={false}
        autoCapitalize="none"
        clearButtonMode="never"
        selectionColor={theme.colors.primary}
      />
      {value ? (
        <Pressable
          onPress={handleClear}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel={t('ui.clear')}
          style={styles.action}
        >
          <MaterialCommunityIcons
            name="close-circle"
            size={18}
            color={theme.colors.textTertiary}
          />
        </Pressable>
      ) : null}
      {onVoicePress ? (
        <Pressable
          onPress={onVoicePress}
          disabled={voiceDisabled}
          hitSlop={10}
          accessibilityRole="button"
          accessibilityLabel={voiceActive ? t('search.voiceStop') : t('search.voiceInput')}
          accessibilityState={{ disabled: voiceDisabled }}
          style={[styles.action, voiceDisabled && { opacity: 0.4 }]}
        >
          <MaterialCommunityIcons
            name={voiceActive ? 'stop-circle-outline' : voiceDisabled ? 'microphone-off' : 'microphone'}
            size={20}
            color={voiceActive ? theme.colors.danger : theme.colors.primary}
          />
        </Pressable>
      ) : null}
    </View>
  );
});

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    borderWidth: 1,
    height: 46,
    paddingHorizontal: 14,
    ...Platform.select({
      ios: {
        shadowColor: '#000',
        shadowOpacity: 0.06,
        shadowRadius: 10,
        shadowOffset: { width: 0, height: 3 },
      },
      android: { elevation: 2 },
    }),
  },
  // В режиме кнопки контейнер должен занимать всю доступную ширину
  buttonMode: { alignSelf: 'stretch' },
  leftIcon: { marginRight: 8 },
  input: {
    flex: 1,
    fontSize: 15,
    paddingVertical: 0,
    // На Android без явной высоты текст в однострочном поле прижимается к верху
    height: 44,
    textAlignVertical: 'center',
  },
  placeholder: {
    flex: 1,
    fontSize: 14.5,
  },
  action: { marginLeft: 8, padding: 2 },
});

export default SearchBar;
