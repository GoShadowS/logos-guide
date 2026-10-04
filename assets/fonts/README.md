# Шрифты приложения

В приложении используются **системные шрифты** (`System` на iOS, `Roboto` на Android),
поэтому отдельные файлы шрифтов не нужны — папка намеренно пуста.

Если понадобится фирменный шрифт (например, тот, что использован в макетах из `Design/`):

1. Положите файлы `.ttf`/`.otf` в эту папку, например `Inter-Regular.ttf`.
2. Установите `expo-font` (уже есть в зависимостях) и подключите их в `src/App.js`:

   ```js
   import { useFonts } from 'expo-font';
   import * as SplashScreen from 'expo-splash-screen';

   const [loaded] = useFonts({
     'Inter-Regular': require('../../assets/fonts/Inter-Regular.ttf'),
   });
   ```

3. Добавьте семейство шрифта в `src/theme/index.js` (например, `fontFamily: 'Inter-Regular'`)
   и используйте в `typography`.
