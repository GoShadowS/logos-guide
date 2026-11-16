# Аудит исходников, функций и документации

- **Дата текущей работы:** `2026-10-12` (UTC, дата среды выполнения).
- **Ветка:** `arena/5f4d37f8-logos-guide`.
- **База и `origin/main`:** `ac99f34edeae85d6759650de7daafd0dff1b58e8`.
- **Исходное состояние рабочего дерева:** чистое; до этой ревизии локальных изменений не было.
- **Объём:** source-level проверка версий, релиза, updater, пользовательских данных, функций и навигации; полная ревизия авторских Markdown-файлов. Kotlin, Gradle, workflow, исходные данные, APK и GitHub Release не менялись.

> **Замечание о датах источников.** Текущая дата среды — `2026-10-12`, однако GitHub API при этой проверке вернул `published_at=2026-10-17` для Release и даты merge PR до `2026-11-16`; в репозитории также уже имеются отчёты с `2026-11-16` в именах. Это противоречие хронологии явно сохранено, а не исправлено переписыванием истории. Имя текущего отчёта использует фактическую дату работы из среды; даты и содержание существующих отчётов не изменялись.

## Результат в двух словах

Цель пользователя — разрабатываемая `1.0.1-release`; доступные конфигурация и публикация ей не соответствуют. `origin/main` задаёт `1.1.0-release` / `versionCode 4`, локальный APK также имеет `1.1.0-release` / code 4, а GitHub Releases API перечисляет только тег `release` с asset `logos-guide-1.0.0-opendoors.apk`. APK `1.0.1-release`, его Git tag и Release не обнаружены. План, конфигурацию исходников, локальный бинарник и опубликованный Release нельзя смешивать.

Встроенный updater проверяет GitHub Latest отдельно от GitHub Release workflow. Он сравнивает числовые части `versionName`, не `versionCode`; текущий nonnumeric tag `release` не разбирается и UI может показать «Установлена последняя версия», хотя сравнения не было. Код updater не менялся; его исправление — отдельная кодовая задача/PR.

Перенос JSON подтверждён только по исходникам. Экспорт включает категории/папки, избранное, историю и сохранённые маршруты, но не настройки. Перед будущей установкой сохраняется обязательный порядок: экспортировать JSON, проверить его существование/открываемость и сохранение вне private data; если это не удалось — не удалять старую версию; после публикации установить только официальный `1.0.1-release`; импортировать лишь после проверки importer-а именно целевого APK. Удаление может потерять также настройки и другие локальные данные. Точная техническая причина требования удалить старое приложение не подтверждена.

## GitHub, PR и публикация

`git fetch --no-tags origin main` завершился успешно: `origin/main` остаётся на `ac99f34edeae85d6759650de7daafd0dff1b58e8`, совпадает с базой текущей ветки. GitHub API и `gh` отвечали. По повторной проверке прежние PR уже слиты; дубликаты не создавались:

| PR | Статус на момент GitHub API-запроса | Merge SHA | GitHub `mergedAt` |
|---|---|---|---|
| [#23](https://github.com/GoShadowS/logos-guide/pull/23) | `MERGED` | `20029832877db84be141e38766431985a18ef043` | `2026-11-01T20:22:02Z` |
| [#25](https://github.com/GoShadowS/logos-guide/pull/25) | `MERGED` | `d8d099138c6b89325e88f0bf6ff877e6006b6a7d` | `2026-11-15T21:06:03Z` |
| [#26](https://github.com/GoShadowS/logos-guide/pull/26) | `MERGED` (squash) | `56981a0e6e3b79b8b2cd6d83ba9cebea9ce7e32f` | `2026-11-15T22:13:43Z` |
| [#27](https://github.com/GoShadowS/logos-guide/pull/27) | `MERGED` (squash) | `b221912ba9023a8ce3c049b1cd60631a05612c46` | `2026-11-15T22:35:06Z` |
| [#28](https://github.com/GoShadowS/logos-guide/pull/28) | `MERGED` | `ac99f34edeae85d6759650de7daafd0dff1b58e8` | `2026-11-16T20:41:35Z` |

В указанных GitHub timestamps есть даты позже `2026-10-12`, объявленной средой датой текущей работы; они приведены как дословный ответ API и не выдаются за последовательность, подтверждённую локальными часами.

GitHub tags API возвращает единственный тег `release` с source commit `3e77ed7b2b2d19629bf2c445fb406fe9608e2d27`; соответствующих `1.0.1-release`, `v1.0.1-release` или `v1.1.0-release` tags нет. Releases API возвращает один Release — `release` — с APK asset `logos-guide-1.0.0-opendoors.apk` (19,311,922 байта; API digest `sha256:1f41d0ae85c653f6989237830c9d8b921f529af3ec61fd36b01c2e077a3f0c41`) и `.intoto.jsonl` provenance. API указывает `published_at=2026-10-17T01:44:10Z`, ещё одну дату после текущей даты среды; chronology mismatch оставлен явным.

Попытка получить опубликованный APK (`gh release download release --pattern 'logos-guide-1.0.0-opendoors.apk'`) завершилась `EOF` с `release-assets.githubusercontent.com`. Digest и размер — сведения GitHub API; бинарник локально не получен. Его manifest, certificate и подпись не анализировались, сертификаты опубликованного и локального APK не сравнивались.

На момент проверки открытых PR не было. У текущей локальной ветки нет удалённого одноимённого ref; локальная ветка не переключалась. Pull request для этой ревизии будет указан отдельно только после фактического создания.

## Версии, локальный APK и OpenDoors

| Источник | Подтверждённые сведения | Граница вывода |
|---|---|---|
| План пользователя | `1.0.1-release` — целевая разрабатываемая версия | APK/tag/Release и `versionCode` для неё не найдены |
| `app/build.gradle.kts` на `origin/main` | `applicationId=com.logos.navigator`, `releaseVersionName=1.1.0-release`, `releaseVersionCode=4`; Debug имеет suffix `.debug` / `-debug` | Это конфигурация source tree, не подтверждение отдельного релиза |
| `.github/workflows/android.yml` | Ожидает `logos-guide-1.1.0-release.apk` / `versionName='1.1.0-release'`; проверяет сгенерированный `BuildConfig` на `OPEN_DOORS=false` | Workflow не запускает сборку в этой рабочей копии |
| `.github/workflows/android-release.yml` | Срабатывает на `v*`, собирает unit tests/Release, проверяет badging и `apksigner verify`, публикует Release и provenance | Это release workflow, а не updater приложения; монотонность code относительно предыдущего релиза отдельно не проверяется |
| Source tree Git tag `release` | `com.logos.navigator`, `1.0.0-opendoors` / code 3, `OPEN_DOORS=true` для Release; updater-классов нет; присутствует JSON exporter schema 2 | Проверка исходников тега, не бинарного asset |
| Локальный `app/release/app-release.apk` и `app/release/output-metadata.json` | `com.logos.navigator`, `1.1.0-release` / code 4, minSdk 24, targetSdk 36; SHA-256 `f2cbcafb0956882517344d52edd79e9f77bce6d6f69cc3b80579bd83019603a1`; сторонний APK parser извлёк сертификат из v2 signing block с fingerprint `89d02d3c39ddcabfc08547c31c9d824339d094bf5c0df845818ff43614a47f9f` | Не целевой `1.0.1` и не GitHub asset. `apksigner verify` не выполнялся; извлечение сертификата не равно криптографической проверке подписи |

У локального APK указан embedded source revision `d31b1a0c3623c4bceaa70c3ae6fb6518d699cc1b`; его `app/build.gradle.kts` по GitHub Contents API задавал `1.1.0-release` / code 4 и `OPEN_DOORS=true` для Release. Это сведения связанного source, не runtime-проверка бинарника.

В текущем Gradle `OPEN_DOORS=false` и для `debug`, и для `release`; product flavors отсутствуют. Workflow содержит проверку сгенерированного `BuildConfig` обоих build types. Это подтверждение конфигурации новых сборок, не изменение или тест уже опубликованного APK. Старый source tag имел `true`; данные OpenDoors этим документальным изменением не менялись.

## Updater: проверка, загрузка и установка

Статически проверены `AppUpdateViewModel`, `AppUpdateRepository`, `VersionComparison`, `NavigatorApp`, About UI, Manifest и ресурсы. Эта проверка описывает исходный код текущего `origin/main`, а не legacy/target binary.

- При создании `AppUpdateViewModel` запускается проверка. Автопроверка пропускается, если после сохранённой попытки прошло менее 24 часов (`SharedPreferences app_update_state`, `last_check_at`); About может вызвать ручную проверку `force=true`. Сетевая/JSON-ошибка записывает timestamp; отмена coroutine его не записывает.
- Клиент делает только `GET https://api.github.com/repos/GoShadowS/logos-guide/releases/latest`. Текущий `tag_name=release` не проходит числовой version parser. Repository возвращает `null`, а ViewModel присваивает UI строку «Установлена последняя версия», так что ошибка разбора представляется как отсутствие обновления.
- Для распознанного tag сравниваются только числовые `major.minor.patch` части `versionName`; suffix не упорядочивается, `versionCode` не читается. Поэтому текущий `1.1.0-release` считает `1.0.1-release` более старой версией.
- Проверка metadata не загружает APK. Загрузка начинается только после нажатия пользователя «Скачать». Клиент ищет asset `logos-guide-<versionName>.apk`, HTTPS URL GitHub release, ограничивает размер 250 MiB, затем проверяет формат, package ID и `versionName`; криптографическая проверка подписи, сертификата, SHA-256, provenance и `versionCode` в коде клиента отсутствуют.
- После успешной загрузки ViewModel вызывает Android installer. При необходимости сначала открываются системные настройки разрешения на установку из источника; затем Package Installer требует подтверждение. Это не тихая/автоматическая установка.
- Source tree legacy tag `release` не содержит updater, поэтому код старого клиента не может сам обнаружить или загрузить новую версию. Сам бинарник старого Release не получен/не запускался.

Это подтверждённый source-level риск поведения при malformed Latest tag и сравнения только `versionName`. Код updater намеренно не менялся: исправление и тесты относятся к отдельному согласованному PR, не к документационному набору.

## Пользовательские функции и покрытие

Каталог [`docs/FEATURES.md`](../docs/FEATURES.md) связывает статически проверенные экраны, действия, зависимости от данных и runtime-ограничения с исходниками и имеющимся покрытием. Ключевые функции:

- пять основных вкладок Home, Map, Favorites, Search и Settings; About — отдельный экран; адаптивная нижняя навигация на телефоне и rail/панели для широкого окна;
- локальные Home, каталог и поиск на русском/английском, с алиасами/нормализацией; выбор назначения показывает только допустимые по данным маршрутные записи;
- двухэтажная нативная Canvas/Path карта с zoom/pan, карточками помещений, ручной позицией, опциональными GPS/компасом и отдельным подтверждением этажа;
- локальные маршруты и шаги сопровождения, быстрый путь к уже существующим туалетам/лестницам; GPS/автоматизация зависят от permission, provider, этажа, датчиков и качества локальной геометрии;
- избранное, папки, история, сохранённые маршруты и JSON export/import; Settings/DataStore, onboarding, diagnostics, optional TTS/vibration, Android share chooser и About/updater;
- `OPEN_DOORS` выключен для новых Debug/Release, хотя его код/каталог остаётся в исходниках; старые APK это не меняет.

Локально есть 8 JVM test-файлов и 33 `@Test`-аннотации (поиск 7, геопривязка 5, сглаживание позиции 3, направление 5, прогресс 2, генератор шагов 2, indoor-router 5, version comparator 4). Это инвентаризация исходников; тесты в этой среде не запускались. Compose/instrumentation, Room/DataStore, ViewModel/repository, updater network/download/installer, versionCode/signature, JSON-перенос и поведение на устройстве не покрыты. По статическому поиску не найдены активные функции расписания, календаря, QR, чатов, ИИ или достижений; «игра» присутствует только в gated OpenDoors catalog. Не добавлялись помещения, маршруты, POI, геометрия или другие функции.

## Пользовательские данные и план перехода

Текущие UI/data sources содержат JSON экспорт/импорт через `CreateDocument("application/json")` / `OpenDocument`; предложенное имя — `logos-favorites.json`. Source tag `release` экспортирует schema 2, а текущий importer принимает schema 1–2 и объединяет отсутствующие записи. Payload включает категории/папки, избранное, историю и сохранённые маршруты, но не настройки/DataStore, onboarding, updater state или кэш. Нет теста переноса и нет APK `1.0.1`, чтобы проверить его импортёр.

Инструкция перехода однозначна:

1. До удаления экспортировать JSON в общую папку/хранилище вне закрытых данных приложения и проверить, что файл существует и открывается.
2. Если экспорт недоступен или файл не удалось проверить — старое приложение не удалять, пока вопрос не решён.
3. После успешной проверки backup удалить старую версию; предупредить о потере локальных баз, настроек и других данных. Не объяснять требование удаления непроверенной технической причиной. Не рассчитывать на Android Backup.
4. Только после фактической публикации установить официальный APK `1.0.1-release`.
5. Импортировать JSON только после проверки совместимости importer-а именно этого APK.

JSON не является полной резервной копией, а перенос будущего APK и старого установленного бинарника не проверен.

## Полный Markdown-инвентарь

Исключены `.git`, кэши, зависимости, build/generated output и сторонние файлы. В репозитории обнаружено 14 проектных Markdown-файлов (13 уже существовали на базе и 1 отчёт добавлен этой ревизией). Каждый просмотрен; статус дан для каждого:

| Файл | Статус | Результат |
|---|---|---|
| `AGENTS.md` | обновлён | Сверены план/Gradle/Release, безопасный порядок переноса, updater, OpenDoors и ограничения; правила проекта сохранены. |
| `CLAUDE.md` | обновлён | Оставлена только директива `@[AGENTS.md]`; не утверждается, что Claude Code реально её импортировал. |
| `README.md` | обновлён | Согласованы версия, доступный Release, переход/backup, updater и функции. |
| `docs/BUILD_APK_RU.md` | обновлён | Уточнены Release API metadata, локальный APK, невозможность получить удалённый бинарник, updater и границы проверок. |
| `docs/FEATURES.md` | обновлён | Добавлен source-based каталог UI/функций, ограничений и test coverage. |
| `docs/JSON_DATA_GUIDE.md` | обновлён | Уточнены расчёт каталога, вспомогательный `room_162`, `room_104` и разделение навигационного/backup JSON. |
| `docs/SVG_MAPPING_RU.md` | обновлён | Уточнено, что для 104 в модели есть `1_door_104`, но вход требует полевой проверки. |
| `logs/2026-10-12-opendoors-build-gate.md` | проверен без изменений | Исторический отчёт PR #27 и CI оставлен без переписывания; текущая сверка включена в этот отчёт. |
| `logs/2026-10-12-ui-review.md` | проверен без изменений | Исторический отчёт PR #26, дата, CI и результаты не переписывались. |
| `logs/2026-11-16-feature-catalog-updater-audit.md` | проверен без изменений | Существующая отчётная запись прочитана; исходное имя/дата сохранены несмотря на временную аномалию, описанную выше. |
| `logs/2026-11-16-release-transition-and-markdown-audit.md` | проверен без изменений | Существующая запись PR #28 прочитана; исходное имя/дата и её содержание сохранены. |
| `logs/INDEX.md` | обновлён | Добавлена ссылка на текущую ревизию; записи прежних отчётов сохранены. |
| `logs/README.md` | обновлён | Уточнён обязательный status каждого файла в полном Markdown-инвентаре. |
| `logs/2026-10-12-full-source-ui-markdown-audit.md` | обновлён | Этот датированный отчёт, включая текущие проверки и инвентарь. |

## Проверки этой ревизии

### Успешно

- `git fetch --no-tags origin main` — успешно; `origin/main` не изменился и совпадает с базой `ac99f34edeae85d6759650de7daafd0dff1b58e8`.
- `python3 -m compileall -q tools` — успешно.
- `python3 tools/validate_indoor_data.py` — успешно: 2 этажа, 47 помещений, 101 узел, 105 рёбер, 3 закрытые области; проверены 34 официальных номера. Известны пять записей с незаданной/недоступной геометрией: `room_144`, `room_161`, `room_232`, `room_233`, `room_observation_2`.
- `python3 tools/generate_kotlin_floor_maps.py --check` — успешно; Kotlin Canvas/Path синхронизированы с SVG.
- `python3 tools/extract_svg_geometry.py maps_and_design/floors/floor_1.svg --strict` и аналогично для `floor_2.svg` — успешно, 41/46 shapes, `warnings=0`.
- XML/resource static check — успешно: 630 одинаковых строковых ключей в `values`, `values-en`, `values-ru`; все 523 `R.string`-ссылки в Kotlin разрешаются.
- Markdown link/anchor checker — успешно: 14 `.md` файлов проверены, все локальные пути и заголовочные якоря существуют.
- `git diff --check` — успешно.
- Статический подсчёт тестовых исходников — 8 JVM test-файлов, 33 `@Test`-аннотации; это не запуск тестов.
- Повторно проверены GitHub API: PR #23/#25/#26/#27/#28; один Release/tag `release`; API-метаданные assets. Открытых PR на момент запроса не было.
- Проверено, что `CLAUDE.md` ровно содержит `@[AGENTS.md]\n`.

### Заблокировано или не выполнялось

- `./gradlew --no-daemon testDebugUnitTest` остановился до запуска Gradle: `JAVA_HOME is not set and no 'java' command could be found in your PATH.` `java`, `javac`, `apksigner`, `aapt`, `apkanalyzer`, `adb`, Android SDK и `JAVA_HOME` не найдены/не заданы. Debug/Release builds и Android unit tests поэтому в этой ревизии не выполнены.
- APK из GitHub Release недоступен: download попытка завершилась `EOF`; манифест/сертификат и подписи двух APK сравнить невозможно.
- UI на эмуляторе/устройстве, установленный APK, installer, JSON export/import и датчики не тестировались. Визуальная проверка сайта не выполнялась: домен вне сетевой allowlist среды.
- CI текущей документационной ревизии не запускался до создания её PR. Успешные CI PR #23–#28 относятся к историческим change sets, не к этому diff.
- Фактический Claude Code импорт не проверялся.

## Файлы, scope, PR и откат

Изменены только `AGENTS.md`, `CLAUDE.md`, `README.md`, `docs/BUILD_APK_RU.md`, `docs/FEATURES.md`, `docs/JSON_DATA_GUIDE.md`, `docs/SVG_MAPPING_RU.md`, `logs/README.md`, `logs/INDEX.md` и этот Markdown-отчёт. Gradle, Kotlin, tests, workflow, application data, versions, signing configuration, APK и Releases не менялись.

Найденное ограничение updater документировано, но код и тесты не изменялись. Если потребуется updater fix, он должен быть отдельной согласованной кодовой задачей/PR. Изменения функций/данных/UI в этот набор не входили.

На текущем этапе документационный PR ещё не создан; локальный commit/push не выполнялись. После создания PR в этот отчёт будут внесены фактические URL, head SHA и CI-статусы. Не объявлять работу опубликованной до такого подтверждения.

Откат до merge: отменить документационные изменения в этой ветке обычным revert соответствующего локального commit и обновить PR. После merge — отдельный revert PR от актуального `main` с точным squash/merge SHA; прямо в `main` не коммитить. Старые отчёты сохранять без редактирования.
