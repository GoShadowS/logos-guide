# План перехода на `1.0.1-release` и полная ревизия Markdown

- **Дата:** `2026-11-16` (дата фактической работы; GitHub timestamps PR приведены отдельно в UTC).
- **Ветка:** `arena/e8197540-logos-guide`.
- **База:** `origin/main` `b221912ba9023a8ce3c049b1cd60631a05612c46`.
- **Цель:** проверить незакоммиченные документы и PR, согласовать все проектные Markdown с планом `1.0.1-release`, проверить APK/подпись и перенос данных настолько, насколько доступны артефакты, документировать updater и OpenDoors. Изменения ограничены Markdown; Gradle, Kotlin, workflow, подпись, APK и GitHub Release не менялись.

## Git, PR и ветка

Перед правками выполнены `git status --short --branch`, `git branch --show-current`, `git rev-parse HEAD`, `git remote -v`, `git branch -vv`, `git rev-list --left-right --count HEAD...origin/main`, `git diff`, `git diff --cached`, `git ls-files --others --exclude-standard` и `git stash list`. Подтверждено:

- текущая ветка — `arena/e8197540-logos-guide`; `HEAD=b221912ba9023a8ce3c049b1cd60631a05612c46`;
- после первого `git fetch --no-tags origin main` актуальный `origin/main` был `b221912ba9023a8ce3c049b1cd60631a05612c46`; в начале продолжения локальных коммитов сверх базы и stash не было;
- перед push повторный `git fetch --no-tags origin main` обнаружил новый upstream commit `1a3a04b6baaa3bc7ead446be62b6cedf2d8977dd` («Update README for project name and description»), изменивший только заголовок и описание README. Он не менял Gradle, workflow или APK. Изменение включено в текущую ветку безопасным merge `git merge --no-edit origin/main`; локальный merge commit — `1d9780c7ee4079f9c39a3772c7274520f82b44b1`. Рабочее дерево после merge чистое; PR diff относительно нового `origin/main` по-прежнему содержит только 13 Markdown-файлов;
- в начале продолжения было 6 изменённых tracked Markdown-файлов, 2 untracked Markdown-файла и пустой staged diff. Изменения не сброшены, а включены/пересмотрены в этом обновлении;
- `gh pr view` подтвердил: PR #23 `MERGED` (merge SHA `20029832877db84be141e38766431985a18ef043`); #26 `MERGED` squash (`56981a0e6e3b79b8b2cd6d83ba9cebea9ce7e32f`); #27 `MERGED` squash (`b221912ba9023a8ce3c049b1cd60631a05612c46`). Повторно создавать/закрывать/сливать эти PR нельзя. Исторические CI-проверки описаны в соответствующих отчётах, они не проверяют этот новый diff.
- GitHub API, `gh` и `git fetch` отвечали успешно. Официальный endpoint `githubstatus.com` отдельно не проверялся; оснований заявлять outage в этой задаче нет.

## Подтверждённое состояние версии и APK

Пользователь сообщил, что целевая разрабатываемая версия — **`1.0.1-release`**. Это план, а не подтверждение собранного/опубликованного релиза. Проверенные источники расходятся:

| Источник | Результат сверки |
|---|---|
| `app/build.gradle.kts` на текущем `origin/main` | `releaseVersionName="1.1.0-release"`, `releaseVersionCode=4`, `applicationId="com.logos.navigator"`. Не изменялись. |
| `.github/workflows/android.yml` | Проверяет APK `logos-guide-1.1.0-release.apk` и `versionName='1.1.0-release'`. |
| `.github/workflows/android-release.yml` | Запускается на push tag `v*`, ищет APK по значению тега, сверяет `versionName`/`apksigner verify`, но не сравнивает новый `versionCode` с предыдущим. Сборка под целевую `1.0.1-release` в этой задаче не выполнялась. |
| Удалённые Git tags / Releases | `git ls-remote --tags origin` и GitHub API tags вернули только `release`. `gh release list --limit 100` и Releases API вернули один опубликованный Release tag `release`, asset `logos-guide-1.0.0-opendoors.apk` и SLSA asset; `1.0.1-release` не найден. APK asset API metadata: 19,311,922 bytes, сообщённый API digest `sha256:1f41d0ae85c653f6989237830c9d8b921f529af3ec61fd36b01c2e077a3f0c41` (локально не пересчитан). Source tree тега задаёт `1.0.0-opendoors` / code 3, но это не извлечение метаданных самого удалённого бинарника. |
| Локальные APK | `find . -type f -iname '*.apk'` нашёл только `app/release/app-release.apk`. APK parser извлёк `package=com.logos.navigator`, `versionName=1.1.0-release`, `versionCode=4`, `minSdk=24`, `targetSdk=36`; SHA-256 файла `f2cbcafb0956882517344d52edd79e9f77bce6d6f69cc3b80579bd83019603a1`. Обнаружен v2 signing block и сертификат с SHA-256 `89d02d3c39ddcabfc08547c31c9d824339d094bf5c0df845818ff43614a47f9f`; embedded source revision `d31b1a0c3623c4bceaa70c3ae6fb6518d699cc1b` по GitHub Contents API задавал `1.1.0-release` / code 4 и release `OPEN_DOORS=true`. Это проверка исходника, не runtime бинарника. `apksigner`, `aapt` и Android SDK недоступны; извлечение сертификата не заменяет `apksigner verify`. |

Target APK `1.0.1-release` отсутствует; опубликованный старый Release APK не скачан и не разобран. Установить факт несовпадения сертификатов/package ID или объяснить им обязательное удаление нельзя. В пользовательской инструкции сохранено обязательное удаление старой версии по указанию пользователя, но конкретная техническая причина помечена как неподтверждённая.

## Экспорт, импорт и риск данных

В исходниках опубликованного source tag `release` и текущего `origin/main` проверены `FavoritesScreen`, `FavoritesViewModel` и `FavoritesRepository`:

- В старом source tag предусмотрены кнопки экспорта/импорта, системный `CreateDocument("application/json")` с предлагаемым именем `logos-favorites.json` и `OpenDocument`.
- Export schema 2 включает категории/папки, избранное, историю и сохранённые маршруты. Настройки, onboarding, updater preferences и кэш в JSON не входят.
- Current `origin/main` importer принимает schema 1–2 и сливает отсутствующие записи по категориям/именам, `roomId`, `toRoomId`/timestamp и endpoints/`createdAt` маршрута.
- Это source-level проверка, не запуск установленного старого APK и не подтверждение конкретного импортёра APK `1.0.1`. Экспорт/импорт и перенос на устройстве не тестировались. JSON необходимо сохранить в Documents/Downloads или другом хранилище вне private data приложения; удаление может стереть остальные Room/DataStore/SharedPreferences данные. `allowBackup=true` в манифесте не трактуется как гарантия системного восстановления.

В README и сборочном руководстве добавлены обязательная последовательность «экспорт → удалить старую версию → установить официальный APK после выпуска → импорт при подтверждённой совместимости», заметное предупреждение о возможной потере данных и отсутствие ссылки на будущий APK.

## Автообновление и обнаруженное ограничение

По `AppUpdateViewModel`, `AppUpdateRepository`, `VersionComparison`, `NavigatorApp`, About UI и ресурсам текущий код:

- запускает проверку при старте, не чаще раза в 24 часа после сохранённой попытки; ручная проверка в About обходит интервал; обработанная HTTP/JSON-ошибка тоже обновляет timestamp;
- запрашивает только GitHub `releases/latest`; APK не скачивает без нажатия пользователя «Скачать»;
- сравнивает числовые части `versionName`; не читает/не сравнивает `versionCode`, не проверяет signing certificate, SHA-256 или SLSA;
- после явного скачивания автоматически открывает Android Package Installer, но требует разрешение Android на установку из этого источника и подтверждение пользователя;
- возвращает `null` для некорректного тега, после чего UI может показать «Установлена последняя версия». Фактический Latest tag `release` не проходит числовой формат, поэтому текущий ответ не подтверждает успешное сравнение версии;
- не способен самостоятельно обновить установленный legacy `1.0.0-opendoors`: source tree этого тега не содержит updater. И текущая конфигурация updater `1.1.0-release` считает целевую версию `1.0.1-release` более старой по числовому `versionName`.

Подтверждён дефект требований к updater: проверка по `versionCode` отсутствует, обработка некорректного latest tag вводит в заблуждение. Код не изменён, так как исправление должно быть отдельным кодовым PR с тестами и не смешиваться с документационной задачей. В этой Arena-сессии работа ведётся только в текущей ветке; отдельная ветка для updater-fix не создавалась. Требуется отдельная разрешённая задача/ветка для исправления и тестирования; PR #23 и его исторический CI не покрывают этот будущий код.

## OpenDoors и каталог

- `app/build.gradle.kts` содержит ровно два `buildConfigField(...OPEN_DOORS..., "false")`: `debug` и `release`; product flavors отсутствуют. CI workflow статически собирает/проверяет варианты, а исторический PR #27 завершился успешно по `build`/`validate`.
- Значение относится к новым сборкам, не меняет старые APK. Исходники legacy тега `release` задавали `OPEN_DOORS=true` в `release`; runtime старых APK не запускался.
- Единый source-based каталог подтверждённых функций и покрытия находится в [`docs/FEATURES.md`](../docs/FEATURES.md). Запрещённые несвязанные функции и выдуманные данные не добавлялись.

## Полный Markdown-инвентарь и итог

В начале продолжения `git ls-files '*.md' '**/*.md'` показал 10 tracked файлов, а `find` — 12 Markdown в рабочем дереве, включая 2 untracked. После добавления текущего task report найдено 13 проектных Markdown; `.git`, build/cache, dependencies и generated files в инвентарь не входят. По каждому файлу:

| Путь | Итоговый статус |
|---|---|
| `AGENTS.md` | Обновлён: версия-план против Gradle, перенос данных, OpenDoors, updater и ограничения проверок. |
| `CLAUDE.md` | Обновлён синхронно с `AGENTS.md`. |
| `README.md` | Обновлён; добавлены текущий статус, обязательные шаги перехода и предупреждения о данных. |
| `docs/BUILD_APK_RU.md` | Обновлён как основной источник правды по версии, APK, переносу и updater. |
| `docs/FEATURES.md` | Обновлён единый source-based каталог, экспорт/импорт, OpenDoors, updater и покрытие. |
| `docs/JSON_DATA_GUIDE.md` | Обновлена граница документа: навигационные JSON не путать с резервным пользовательским JSON; ссылка на переход. |
| `docs/SVG_MAPPING_RU.md` | Обновлена граница геометрического документа и ссылка на статус/установку. |
| `logs/2026-10-12-ui-review.md` | Добавлена пометка исторического отчёта PR #26 и ссылка на текущий статус; даты/CI/результаты не переписывались. |
| `logs/2026-10-12-opendoors-build-gate.md` | Добавлены историческая маркировка и датированный текущий статус; результаты PR #27 сохранены. |
| `logs/INDEX.md` | Обновлены дата/путь аудита и индекс нового отчёта. |
| `logs/README.md` | Обновлены правила дат, версии, data transfer, updater и PR/CI отчётности. |
| `logs/2026-11-16-feature-catalog-updater-audit.md` | Переименован с ошибочной даты `2026-10-12`, актуализирован проверенными release/updater/data фактами. |
| `logs/2026-11-16-release-transition-and-markdown-audit.md` | Этот новый отчёт; содержит инвентарь, факты и результаты этой задачи. |

`find` на старте не обнаружил Markdown в `.github` или дополнительных вложенных/генерируемых каталогах. Файлы `.git`, кэши, `build/`, зависимости и сторонние автоматически созданные документы исключены по требованиям; других найденных `.md` исключений нет.

## Проверки

### Выполнено в этой рабочей копии

- `git fetch --no-tags origin main`; сравнение `HEAD` и `origin/main` — один SHA, diverge `0/0`.
- `gh pr view 23/26/27` по отдельности — все три PR имеют `MERGED`; `gh release list --limit 100`, `gh api repos/GoShadowS/logos-guide/releases?per_page=100`, `gh api repos/GoShadowS/logos-guide/tags?per_page=100`, `git ls-remote --tags origin` — один опубликованный release/tag `release`, никакой `1.0.1-release` не найден.
- `python3 -m compileall -q tools` — успешно.
- `python3 tools/validate_indoor_data.py` — успешно: 2 этажа, 47 помещений, 101 узел, 105 рёбер, 3 закрытые области; проверены 34 официальных номера; перечислены 5 недоступных по геометрии записей.
- `python3 tools/generate_kotlin_floor_maps.py --check` — успешно; карты синхронизированы с SVG.
- `python3 tools/extract_svg_geometry.py maps_and_design/floors/floor_1.svg --strict` и аналогично для `floor_2.svg` — обе команды завершились с code 0 и `warnings: []`.
- XML/localization Python-проверка: три файла по 630 одинаковых строковых ключей, все 523 использованные `R.string` существуют.
- Статическая Python-сверка Gradle/workflow: версия `1.1.0-release` / code 4, `applicationId`, ровно два `OPEN_DOORS=false`, product flavors нет; Android CI хардкодит `1.1.0`; release workflow использует `v*`, вызывает `apksigner`, не содержит проверки возрастания `versionCode`.
- APK metadata parse локального `app/release/app-release.apk`: сведения приведены выше; это извлечение метаданных, не Android build, `apksigner verify` или device test.
- Экспорт/импорт: статический просмотр source tag `release` и current `origin/main`; перенос на реальном устройстве не запускался.

### Требует повторения/недоступно

- `git diff --check`, Markdown-ссылки/форматирование, финальная проверка всех Markdown и Git status выполняются после последней правки отчёта; результат будет внесён по факту.
- Android Gradle unit tests/Debug/Release build и `apksigner verify` локально недоступны: `command -v java` ничего не вернул, `JAVA_HOME`, `ANDROID_HOME`, `ANDROID_SDK_ROOT` не заданы; ранее `./gradlew --no-daemon testDebugUnitTest` прекращался до запуска Gradle по причине отсутствия Java. В этой задаче Gradle повторно не запускался, потому что среда не изменилась.
- Runtime/устройство, импорт реального backup, старый/целевой APK, подпись GitHub Release binary, автообновление/скачивание/установка не проверялись. Целевой `1.0.1` APK отсутствует.
- На момент этой сверки документационный PR ещё не создан; его CI/review и merge SHA отсутствуют. После PR будут указаны реальные результаты; CI старых #23/#26/#27 не используется как результат нового diff.

## PR, откат и оставшийся блокер

На момент написания первоначального варианта отчёта документационный PR ещё не создан; новые изменения не слиты. После создания документационного PR сюда будет внесён его настоящий номер, статус, проверенный CI/review и merge SHA (если merge фактически произойдёт). Не публиковать APK/Release.

Updater code PR не включён и отдельной ветки в этой сессии не создано. Это оставшийся блокер для исправления versionCode comparison и некорректного latest tag; отдельный кодовый PR должен иметь собственные тесты и актуальный CI.

Откат уже слитых PR #23/#26/#27 указан выше в [отчёте аудита updater](2026-11-16-feature-catalog-updater-audit.md). Для будущего документационного squash-merge откатить только документальный commit командой `git revert <фактический-squash-merge-SHA>` в отдельной ветке и открыть revert PR; до merge фактического SHA нет. Команды отката не выполнялись.
