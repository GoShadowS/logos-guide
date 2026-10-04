 # Сборка приложения «ЛОГОС: Путеводитель»

Пошаговые инструкции для трёх способов: **EAS Build (облако, без терминала и без Mac)**,
**Android Studio (GUI)**, **Xcode (GUI)** и автоматическая сборка через **GitHub Actions**.

> 💡 Рекомендуемый способ — **EAS Build**: не нужно ничего устанавливать, кроме браузера,
> и он даёт и `.apk` для Android, и `.ipa` для iOS.

---

# 1. Подготовка (один раз)

### 1.1. Аккаунты

| Аккаунт | Зачем | Стоимость |
|---|---|---|
| [expo.dev](https://expo.dev/signup) | облачная сборка EAS | бесплатно |
| [GitHub](https://github.com) | хранение кода, автосборка | бесплатно |
| Google Play Console | публикация в Google Play | $25 один раз |
| Apple Developer Program | публикация в App Store | $99/год |

### 1.2. Установка EAS CLI (только если собираете из терминала)

```bash
npm ci
npm install -g eas-cli
eas login
```

Команды запускайте из каталога `logos-guide`, где находятся `package.json` и
`package-lock.json`. `npm ci` устанавливает локальные Expo-модули и config plugins
(включая `expo-location`); без этого `eas init` завершается ошибкой `Failed to
resolve plugin`. Если EAS CLI уже установлен, повторно устанавливать его не нужно.

Для сборки **через веб-интерфейс** expo.dev EAS CLI не нужен.

### 1.3. Проверка конфигурации

В файле `app.json` проверьте:

* `expo.name` — «ЛОГОС: Путеводитель»
* `expo.ios.bundleIdentifier` — `ru.logos.guide`
* `expo.android.package` — `ru.logos.guide`
* `expo.version` — `1.0.0`

Android APK настроен для устройств с **Android 8.0 (API 26) и новее** и включает
нативные библиотеки для `armeabi-v7a` (32-bit ARM) и `arm64-v8a` (64-bit ARM).
Один APK из профиля `preview` устанавливается на оба типа устройств.

В `eas.json` уже подготовлены профили:

| Профиль | Назначение | Результат |
|---|---|---|
| `development` | dev-клиент для отладки | `.apk` (debug) |
| `preview` | тестовая сборка для установки на телефон | `.apk` / `.ipa` |
| `production` | публикация в магазины | `.aab` (Android) / `.ipa` (iOS) |

> **Привязка EAS-проекта.** Перед первой сборкой из терминала выполните `eas init`
> из папки `logos-guide` и выберите Expo-аккаунт и проект. Команда создаст или
> привяжет проект и запишет настоящий `extra.eas.projectId` в `app.json`.
> Фиктивный ID не используется: без привязки к проекту EAS не сможет сохранить
> учётные данные и управлять сборками.


---

# 2. APK для Android через EAS Build (рекомендуется)

### 2.1. Через сайт expo.dev (без терминала)

1. Откройте <https://expo.dev> и войдите в аккаунт.
2. Нажмите **Create a project** (или откройте существующий проект `logos-guide`).
3. Слева выберите **Builds** → нажмите **Create a build**.
4. Заполните поля:
   * **Platform:** Android
   * **Git clone URL:** `https://github.com/GoShadowS/LOGOS-Guidebook.git`
   * **Branch:** `main` (или ваша ветка)
   * **Build profile:** `preview`
   * **Directory / app root:** `logos-guide`  ← важно, проект в подпапке!
5. Нажмите **Create build**. Expo скачает репозиторий и запустит облачную сборку.
6. Дождитесь зелёного статуса **Finished** (обычно 8–15 минут).
7. Нажмите **Download** — получите файл `build-XXXX.apk`.

### 2.2. Через терминал (альтернатива)

```bash
cd logos-guide
npm ci
eas login
eas build --platform android --profile preview
```

По завершении команда выдаст ссылку на скачивание `.apk`.

Если EAS сообщает `git command not found` или что `git --help` завершился с
ошибкой, в PowerShell можно запустить сборку без VCS:

```powershell
$env:EAS_NO_VCS = "1"
eas build --platform android --profile preview
```

Переменная действует только в текущем окне PowerShell. Не повторяйте `eas init`,
если в `app.json` уже задан `expo.extra.eas.projectId`. Для постоянной работы
лучше установить или восстановить Git for Windows и проверить `git --help`.

### 2.3. Что получится

* `preview` → **APK** для Android 8+ (API 26), ARM 32-bit и 64-bit.
* `production` → **AAB** (Android App Bundle — для Google Play; APK из него не извлекается,
  поэтому для тестирования используйте профиль `preview`).

---

# 3. IPA для iOS через EAS Build (Mac не нужен)

### 3.1. Что понадобится

* Аккаунт **Expo** (бесплатно).
* **Apple ID** и участие в **Apple Developer Program** ($99/год) — требуется для установки
  на реальные устройства и публикации в App Store.

### 3.2. Регистрация Apple Developer Program

1. Откройте <https://developer.apple.com/programs/>.
2. Нажмите **Enroll** и войдите с Apple ID (включите двухфакторную аутентификацию — она обязательна).
3. Выберите **Individual** (частное лицо) или **Organization** (нужно подтверждение юрлица/ДУНС).
4. Оплатите $99/год. Активация — от нескольких часов до 2 дней.

### 3.3. Сборка IPA

1. На <https://expo.dev> откройте проект → **Builds** → **Create a build**.
2. **Platform:** iOS, **Build profile:** `preview` (или `production`).
3. EAS предложит подключить Apple ID:
   * Нажмите **Log in to Apple account** и войдите.
   * Expo создаст сертификаты и provisioning-профили автоматически.
4. Дождитесь **Finished**, нажмите **Download** → получите `build-XXXX.ipa`.

### 3.4. Установка IPA на iPhone без App Store

Способ А — через TestFlight (официальный):
1. `eas submit --platform ios --latest` или кнопка **Submit to TestFlight** на expo.dev.
2. В App Store Connect добавьте себя во внутренних тестировщиков.
3. Установите **TestFlight** из App Store и примите приглашение.

Способ Б — прямая установка ( ad hoc / development профиль):
1. Соберите IPA с профилем `preview` (EAS регистрирует устройства по UDID).
2. Скачайте `.ipa`, переименуйте в `.zip`, распакуйте → получите `Payload/Приложение.app`.
3. Установите через Apple Configurator 2 (Mac), Sideloadly или AltStore.
4. В Настройки → Основные → VPN и управление устройством → доверьтесь разработчику.

---

# 4. Локальная сборка APK в Android Studio (GUI)

Нужно, если хочется собирать без облака.

### 4.1. Установка

1. Установите [Android Studio](https://developer.android.com/studio) (последняя версия).
2. При первом запуске: **More Actions → SDK Manager** →
   * вкладка **SDK Platforms**: отметьте **Android 15 (API 36)**;
   * вкладка **SDK Tools**: отметьте **Android SDK Build-Tools**, **Android Emulator**, **Platform-Tools**.
3. Установите **JDK 17** (в комплекте с Android Studio — «Embedded JDK»).

### 4.2. Генерация нативного проекта (один раз, команда)

Expo управляет нативными проектами, поэтому их нужно сгенерировать:

```bash
cd logos-guide
npx expo prebuild --platform android
```

После этого появится папка `android/` (её не нужно коммитить — она в `.gitignore`).

### 4.3. Сборка APK

1. Откройте папку `logos-guide/android` в Android Studio (**Open**).
2. Дождитесь завершения **Gradle Sync** (индикатор внизу).
3. Меню **Build → Generate Signed Bundle / APK**.
4. Выберите **APK** → **Next**.
5. Создайте keystore: **Create new…**
   * **Key store path:** `logos-guide/release.keystore` (сохраните файл и пароли!)
   * Пароль, alias `logos-key`, страна `RU`, заполните остальные поля.
6. Выберите созданный ключ → **Next** → **release** → **Create**.
7. Готовый файл: `android/app/release/app-release.apk`.

> ⚠️ **Сохраните `release.keystore` и пароли.** Без них нельзя обновить приложение
> в Google Play — придётся выпускать новое.

### 4.4. Установка на телефон

* Скопируйте `.apk` на телефон (USB, Telegram, облако) и откройте файлом.
* Разрешите «Установка из неизвестных источников» для файлового менеджера.
* Либо: включите **Режим разработчика** (7 тапов по номеру сборки) → **Отладка по USB** →
  `adb install app-release.apk` из терминала Android Studio.

---

# 5. Локальная сборка IPA в Xcode (нужен Mac)

### 5.1. Установка

1. Установите **Xcode** из Mac App Store (версия 16.1+).
2. Установите **CocoaPods**: `sudo gem install cocoapods` (один раз).
3. Выполните предварительную генерацию проекта:

```bash
cd logos-guide
npx expo prebuild --platform ios
cd ios && pod install && cd ..
```

### 5.2. Сборка

1. Откройте `logos-guide/ios/LOGOSGuide.xcworkspace` (именно `.xcworkspace`, не `.xcodeproj`).
2. Вверху выберите target **LOGOSGuide** и устройство **Any iOS Device (arm64)**.
3. Меню **Product → Scheme → Edit Scheme → Run → Build Configuration:** `Release`.
4. Меню **Product → Archive**. Дождитесь окончания.
5. Откроется окно **Organizer** → **Distribute App**:
   * **App Store Connect** (для TestFlight/публикации), или
   * **Ad Hoc / Development** (для прямой установки), или
   * **Enterprise** (если есть корпоративный аккаунт).
6. Следуйте мастеру, подпишите сертификатом Apple → получите `.ipa`.

---

# 6. Автоматическая сборка через GitHub Actions

Один раз настраивается — дальше при каждом `push` собирается APK.

### 6.1. Получение токена Expo

1. Откройте <https://expo.dev/settings/access-tokens>.
2. **Create Token** → скопируйте значение.

### 6.2. Добавление секрета в репозиторий

1. GitHub → ваш репозиторий → **Settings → Secrets and variables → Actions → New repository secret**.
2. Имя: `EXPO_TOKEN`, значение: скопированный токен → **Add secret**.

### 6.3. Что происходит автоматически

Файл `.github/workflows/eas-build.yml` в этом проекте:

* при `push` в `main` — сборка Android APK (`preview`);
* при создании тега `v*` — production-сборка Android (`app-bundle`);
* артефакт (APK/AAB) доступен на вкладке **Actions → ваш запуск → Artifacts**.

### 6.4. Ручной запуск

GitHub → вкладка **Actions** → workflow **EAS Build** → **Run workflow** → выберите платформу.

---

# 7. Как установить собранное приложение на телефон

| Файл | Android | iOS |
|---|---|---|
| `.apk` | Скопировать на телефон и открыть (разрешить установку из неизвестных источников) | — |
| `.aab` | Через Google Play (Internal testing) | — |
| `.ipa` | — | TestFlight, Sideloadly, Apple Configurator 2 |

### Android: включение установки из неизвестных источников

**Android 8+:** Настройки → Приложения → Особые разрешения → Установка неизвестных приложений →
выберите файловый менеджер/браузер → разрешить.

---

# 8. Проверка перед публикацией

```bash
# Локальная проверка конфигурации
npx expo-doctor

# Целостность данных, локализации и иконок
npm run validate

# Тесты алгоритмов навигации и поиска
npm test

# Дымовой тест реальной сборки (приложение запускается в jsdom)
npm run smoke

# Просмотр итоговой конфигурации
npx expo config --type public
```

---

# 9. Типичные ошибки

| Ошибка | Решение |
|---|---|
| `Cannot find module 'babel-preset-expo'` | `npm install --save-dev babel-preset-expo` |
| `Android build failed: SDK location not found` | В EAS Build не бывает; в Android Studio создайте `android/local.properties` с `sdk.dir=/путь/к/Android/sdk` |
| iOS: «No provisioning profiles» | Войдите в Apple ID через EAS (`eas credentials`) — профили создадутся автоматически |
| APK не устанавливается («приложение не установлено») | Удалите старую версию с другим `package`/подписью; проверьте `minSdkVersion` |
| Голосовой поиск не работает | Нужны разрешение на микрофон и установленное системное распознавание речи (на Android — сервисы Google); без них кнопка микрофона остаётся неактивной |

---

## Итоговая шпаргалка

```bash
# Облачная сборка APK для теста
eas build --platform android --profile preview

# Облачная сборка IPA для TestFlight
eas build --platform ios --profile preview

# Production AAB для Google Play
eas build --platform android --profile production

# Отправка в stores
eas submit --platform android --latest
eas submit --platform ios --latest
```
