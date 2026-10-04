/**
 * components/LanguageSwitcher.js — переключатель языка интерфейса.
 * Варианты: «Как в системе», «Русский», «English».
 * Языки обозначаются векторными иконками (эмодзи в интерфейсе не используются).
 */
import React from 'react';
import { SegmentedControl } from './SegmentedControl';
import { useI18n } from '../localization/I18nProvider';
import { LANGUAGES, SYSTEM_LANGUAGE, resolveLanguage } from '../localization/i18n.config';
import { getSystemLanguageCode } from '../services/localization';

export function LanguageSwitcher({ value, onChange, style }) {
  const { t } = useI18n();
  const systemLanguage = resolveLanguage(SYSTEM_LANGUAGE, getSystemLanguageCode());

  const options = [
    {
      value: SYSTEM_LANGUAGE,
      label: `${t('settings.languageSystem')} (${systemLanguage.toUpperCase()})`,
      icon: 'cellphone',
    },
    ...LANGUAGES.map((lang) => ({
      value: lang.code,
      label: lang.nativeName,
      icon: lang.icon,
    })),
  ];

  return <SegmentedControl options={options} value={value} onChange={onChange} style={style} />;
}

export default LanguageSwitcher;
