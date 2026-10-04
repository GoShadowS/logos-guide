/**
 * Конфигурация Babel.
 * babel-preset-expo — стандартный пресет Expo (включает поддержку JSX, TS, импорта ассетов).
 * react-native-reanimated/plugin — ОБЯЗАТЕЛЬНО должен быть последним в списке плагинов,
 * иначе Reanimated не сможет компилировать анимированные стили (worklets).
 */
module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: [
      // Плагин Reanimated обязан идти последним
      'react-native-reanimated/plugin',
    ],
  };
};
