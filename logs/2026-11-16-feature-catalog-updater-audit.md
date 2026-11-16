# Каталог функций, версия и аудит встроенного автообновления

- **Дата аудита:** `2026-11-16` (исправленная дата; предыдущая версия этого нового отчёта ошибочно называлась `2026-10-12-feature-catalog-updater-audit.md`).
- **Ветка:** `arena/e8197540-logos-guide`.
- **База:** `origin/main` `b221912ba9023a8ce3c049b1cd60631a05612c46`.
- **Состояние при возобновлении задачи:** `HEAD` совпадал с первоначальным `origin/main` `b221912ba9023a8ce3c049b1cd60631a05612c46`; локальных коммитов сверх базы и staged diff не было. Workspace snapshot показал 10 изменённых tracked Markdown-файлов и 3 untracked Markdown-файла; изменений кода не было. Изменения сохранены и пересмотрены; это не было чистым рабочим деревом.
- **Цель:** проверить PR #23/#26/#27, фактическую версию и публикацию, каталог функций, экспорт/импорт пользовательских данных, updater и OpenDoors; не менять код, версии, workflow, подпись, APK или Release.

## PR и GitHub Releases

Текущие PR-статусы повторно проверены `gh pr view` 2026-11-16. Даты merge — GitHub timestamps в UTC; CI ниже — результаты соответствующих исторических PR, а не текущей документационной правки.

| PR | Текущий статус | Итоговый SHA / CI |
|---|---|---|
| [#23](https://github.com/GoShadowS/logos-guide/pull/23) — updater | `MERGED`, `mergedAt=2026-11-01T20:22:02Z` | Merge commit `20029832877db84be141e38766431985a18ef043`; прежний отчёт фиксирует успешные `build`/`validate` runs. |
| [#26](https://github.com/GoShadowS/logos-guide/pull/26) — Compose UI | `MERGED`, squash, `mergedAt=2026-11-15T22:13:43Z` | `56981a0e6e3b79b8b2cd6d83ba9cebea9ce7e32f`; исторические `build`/`validate` runs приведены в [отчёте UI](2026-10-12-ui-review.md). #26 входит в базу #27; не переоткрывать. |
| [#27](https://github.com/GoShadowS/logos-guide/pull/27) — OpenDoors | `MERGED`, squash, `mergedAt=2026-11-15T22:35:06Z` | `b221912ba9023a8ce3c049b1cd60631a05612c46`; исторические `build`/`validate` runs приведены в [отчёте OpenDoors](2026-10-12-opendoors-build-gate.md). |

Первичный `git fetch --no-tags origin main` завершился успешно; на момент этого снимка `HEAD=origin/main=b221912ba9023a8ce3c049b1cd60631a05612c46`, расхождение `0/0`. Перед push обнаружился README-only commit `1a3a04b6baaa3bc7ead446be62b6cedf2d8977dd`; он включён в рабочую ветку безопасным merge и не меняет приведённые build facts. Последний повторный запрос tags и Releases API по-прежнему показал только tag/Release `release`, asset `logos-guide-1.0.0-opendoors.apk` и provenance asset; записи для `1.0.1-release` нет. API сообщает APK asset размером `19,311,922` bytes и digest `sha256:1f41d0ae85c653f6989237830c9d8b921f529af3ec61fd36b01c2e077a3f0c41`; digest сообщён API и локально не пересчитан, binary не скачан.

## Планируемая версия и доступные APK

План проекта по сообщению пользователя — **`1.0.1-release` в разработке**. Это не собранная/опубликованная версия; доступного APK, GitHub tag и Release с ней не найдено, её `versionCode` неизвестен. Фактические источники расходятся:

- Текущий `app/build.gradle.kts` на `origin/main`: `releaseVersionName="1.1.0-release"`, `releaseVersionCode=4`, `applicationId="com.logos.navigator"`.
- `.github/workflows/android.yml` жёстко проверяет `logos-guide-1.1.0-release.apk`/`versionName='1.1.0-release'`; `.github/workflows/android-release.yml` срабатывает на `v*` и ожидает имя APK, выведенное из тега. Версия/workflow для целевого `1.0.1-release` не согласованы; эти файлы не менялись.
- Source tree единственного GitHub tag `release` задаёт `applicationId=com.logos.navigator`, `versionName=1.0.0-opendoors`, `versionCode=3` и не содержит updater-классов. Это проверка исходников тега, не прямое извлечение метаданных бинарника.
- Единственный локальный APK во всём checkout — `app/release/app-release.apk`, SHA-256 `f2cbcafb0956882517344d52edd79e9f77bce6d6f69cc3b80579bd83019603a1`. Из его манифеста сторонним Python APK-парсером извлечены `package=com.logos.navigator`, `versionName=1.1.0-release`, `versionCode=4`, `minSdk=24`, `targetSdk=36`; в v2 signing block обнаружен один сертификат с SHA-256 `89d02d3c39ddcabfc08547c31c9d824339d094bf5c0df845818ff43614a47f9f`. Embedded source revision `d31b1a0c3623c4bceaa70c3ae6fb6518d699cc1b` проверен GitHub Contents API: его Gradle задавал `1.1.0-release` / code 4 и `OPEN_DOORS=true` для release. Это сведения исходника, не runtime-проверка APK; артефакт не является target `1.0.1` или бинарником GitHub Release.
- `apksigner`, `aapt` и Android SDK отсутствуют. Удалённый Release APK недоступен для анализа в рабочем дереве; сертификат его подписи и сертификат будущего `1.0.1` не сравнивались. Получение сертификата из доступного локального файла не является криптографической проверкой `apksigner verify`.

Из-за отсутствия обоих бинарников для требуемого сравнения нельзя утверждать, что старый и целевой APK отличаются по package ID или сертификату. Обязательная инструкция удалить старую версию всё равно оставлена по указанию пользователя; конкретную техническую причину не выдумывать.

## Перенос пользовательских данных

Проверены исходники legacy tag `release` (`3e77ed7b2b2d19629bf2c445fb406fe9608e2d27`) и текущего `origin/main`:

- Старый tag содержит JSON-export/import в `FavoritesRepository`, UI-кнопки в `FavoritesScreen` и document picker `CreateDocument("application/json")` / `OpenDocument`; предлагается имя `logos-favorites.json`. Сам Release APK не скачивался и UI установленного APK не запускался, поэтому наличие потока в конкретной бинарной копии проверено только по source tree.
- Legacy exporter пишет `schemaVersion=2` и сериализует категории/папки, избранное, историю, сохранённые маршруты. Ни настройки DataStore, ни onboarding, ни состояние updater, ни кэш в этом payload не входят.
- Current `origin/main` importer принимает schema 1–2; source-level логика сопоставляет категории по имени, избранное по `roomId`, историю по `toRoomId`/timestamp, маршруты по endpoints/`createdAt`, и объединяет отсутствующие записи. У importer/exporter нет тестов.
- Поэтому подтверждены действия source-level для резервной копии и совместимость legacy schema 2 с **текущим исходником**. Совместимость именно будущего APK `1.0.1-release` и фактическое восстановление на устройстве не проверены: APK отсутствует. Инструкция требует до удаления экспортировать JSON, проверить его существование/открываемость вне закрытых данных приложения, не удалять старую версию при невозможности такой проверки, а импортировать только после проверки target importer. Android Manifest имеет `allowBackup=true`, но системный backup/restore при uninstall/reinstall не тестировался и не обещан.

## Встроенный updater: фактическое поведение и выявленная проблема

В исходниках PR #23 updater существует в Kotlin и вызывается из `NavigatorApp`; это отдельный от GitHub Actions механизм.

1. `AppUpdateViewModel.init` запускает автоматическую проверку при старте. `SharedPreferences` `app_update_state` хранит `last_check_at`; повторная автоматическая проверка пропускается в течение 24 часов. В About вызывается ручная `checkForUpdates(force=true)`. Обработанная HTTP/JSON ошибка также записывает время, отмена coroutine — нет; ручная проверка обходит период.
2. Источник только `GET https://api.github.com/repos/GoShadowS/logos-guide/releases/latest`. `tag_name` передаётся в `isNewerVersion` после удаления начального `v`. Регулярное выражение принимает `major.minor.patch` с необязательным суффиксом; сравниваются только три числовые части, суффикс игнорируется. `versionCode` updater не получает и не сравнивает.
3. GitHub Latest API сейчас возвращает tag `release`. Он не соответствует числовому regex: repository возвращает `null`, а ViewModel показывает сообщение `update_up_to_date` («Установлена последняя версия»), а не ошибку некорректного тега. Следовательно, некорректная metadata может выглядеть как подтверждение актуальности без успешного сравнения.
4. Автоматически скачивается только metadata. APK загружается после нажатия пользователем «Скачать»; выбирается точное имя `logos-guide-<versionName>.apk`, HTTPS host/path GitHub Release, размер не более 250 MiB, timeout, APK-формат, package ID, `versionName` и числовое превосходство `versionName`. Client не проверяет `versionCode`, cert, SHA-256 или SLSA. После скачивания ViewModel вызывает открытие Android installer; разрешение установки из этого источника и подтверждение в Package Installer — ручные действия. Android Installer может отклонить подпись/downgrade; клиент заранее не валидирует сертификат.
5. Source tag `release` (legacy `1.0.0-opendoors`) не имеет updater-классов, поэтому старая опубликованная версия сама не обнаружит/не скачает `1.0.1-release`. Текущий Gradle `1.1.0-release` по текущему comparator также не предложил бы кандидат `1.0.1-release` как новый, потому что числовая версия ниже. Сейчас опубликованный nonnumeric tag создаёт дополнительную ложную «последняя версия».

Проверено, что нынешний код не выполняет сравнение `versionCode` и неверно представляет nonnumeric latest tag как отсутствие обновления. Это отдельный дефект updater-кода, а не документации. В текущем документационном наборе исходники updater/тесты не менялись и отдельный updater PR не создавался; исправление требует отдельной разрешённой кодовой ветки/PR, тестов для versionCode и malformed release tag и CI. Не считать старые PR #23 checks проверкой такого будущего исправления.

## OpenDoors и каталог функций

`app/build.gradle.kts` текущего `origin/main` задаёт `OPEN_DOORS=false` в `debug` и `release`; product flavors отсутствуют. Android workflow проверяет сгенерированный BuildConfig для обоих вариантов. Это относится к новым сборкам, не переписывает старые APK. Source tag `release` задавал `OPEN_DOORS=true` для release; runtime бинарников в этой проверке не запускался. Единый source-based каталог возможностей, данных и покрытия: [`docs/FEATURES.md`](../docs/FEATURES.md).

Проверено 8 JVM test-файлов и 33 `@Test`-аннотации. Тестов UI/instrumentation, Room/DataStore, updater network/download, `versionCode`, APK-подписи, Package Installer и реального переноса нет. Исторические результаты PR #23/#26/#27 приведены в таблице выше и в их отчётах; они не подтверждают runtime updater/будущий APK.

## Откат ранее слитых PR

Откат не выполнялся. Если отдельно потребуется откат, только отдельным PR:

- #23: `git revert -m 1 20029832877db84be141e38766431985a18ef043`.
- #26: `git revert 56981a0e6e3b79b8b2cd6d83ba9cebea9ce7e32f`.
- #27: `git revert b221912ba9023a8ce3c049b1cd60631a05612c46` — вернёт прежнее release-значение OpenDoors в исходниках/CI, но не изменит APK, уже распространённые пользователям.

Команды не запускались. Датированный отчёт текущей ревизии всех Markdown, проверок и документационного PR находится в [`2026-11-16-release-transition-and-markdown-audit.md`](2026-11-16-release-transition-and-markdown-audit.md).
