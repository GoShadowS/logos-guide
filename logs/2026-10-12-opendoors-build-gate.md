# Отключение OpenDoors в пользовательских сборках

> **Исторический отчёт о PR #27.** Первоначальные дата, результаты и проверки ниже относятся к тому изменению и его CI, а не к целевой версии `1.0.1-release` или текущему документационному diff. Актуальные пояснения добавлены отдельно.

## Текущий статус на 2026-11-16

- В текущем `origin/main` `OPEN_DOORS=false` для новых Gradle-вариантов `debug` и `release`; это не меняет уже собранные APK. PR #27 остаётся слитым squash-коммитом `b221912ba9023a8ce3c049b1cd60631a05612c46`.
- По плану проекта разрабатывается `1.0.1-release`, но APK/тег/Release этой версии не обнаружены. Фактический Gradle всё ещё задаёт `1.1.0-release` / code 4; см. [сборочное руководство](../docs/BUILD_APK_RU.md).
- Текущий публичный Release tag `release` перечисляет asset `logos-guide-1.0.0-opendoors.apk`; бинарный asset не скачан и не проверен. Единственный локальный `app/release/app-release.apk` извлечённым манифестом сообщает `1.1.0-release` / code 4 и не является целевым APK `1.0.1`.

- **Дата аудита (как указана в первоначальной версии отчёта):** `2026-10-12`
- **Рабочая ветка PR #27:** `arena/94ce8164-logos-guide`
- **Базовый commit ветки:** `d8d099138c6b89325e88f0bf6ff877e6006b6a7d`
- **База PR #27:** `56981a0e6e3b79b8b2cd6d83ba9cebea9ce7e32f` (уже слитый PR #26)
- **Проверенная дата PR #27:** GitHub сообщает merge `2026-11-15T22:35:06Z`; `origin/main` на момент merge — `b221912ba9023a8ce3c049b1cd60631a05612c46`. Текущий `origin/main` после отдельного README-only commit — `1a3a04b6baaa3bc7ead446be62b6cedf2d8977dd`; build configuration не менялась.
- **Цель:** проверить все собираемые варианты OpenDoors и выключить каталог в новых пользовательских APK, не меняя версию, подпись и опубликованные артефакты.

## Результаты аудита

| Серьёзность | Файл/участок | Факт и риск | Решение |
|---|---|---|---|
| Высокая | `app/build.gradle.kts`, `release` | До изменения `debug` задавал `OPEN_DOORS=false`, а `release` — `true`. Других product flavors и build types в Gradle не найдено. | `release` также задаёт `false`; Debug и Release новых сборок выключают эту возможность. |
| Средняя | `ui/map/MapScreen.kt`, `CanvasMapView.kt` | Точка входа, список площадок, карточки и маркеры зависят от `BuildConfig.OPEN_DOORS`; при `false` список площадок пуст, UI не открывается, маркеры не передаются карте. | В runtime-коде ничего не удалялось; добавлена CI-проверка фактически сгенерированных `BuildConfig` всех вариантов. |
| Высокая / ограничение распространения | APK в репозитории и Latest GitHub Release | Файлы уже опубликованных/отслеживаемых APK не перестраивались. Latest GitHub Release по API по-прежнему указывает на тег `release` и asset `logos-guide-1.0.0-opendoors.apk`; в репозитории также есть ранее собранный `1.1.0-release`, versionCode 4. | Не заменять и не публиковать APK без отдельного разрешения. Исправление распространяется на сборки, созданные из версии кода с новым значением; оно не отзывает и не меняет уже доступные файлы. |

`MapScreen.kt` также проверен по всем вхождениям `OPEN_DOORS`; сборочный вариант `release` — тот же, который создают Android CI и release workflow. `OpenDoorsCatalog` и `OpenDoorsSheet` остаются в исходниках, но не имеют активной точки входа в новых сборках с `false`.

## Изменения

- В `app/build.gradle.kts` для `release` установлено `OPEN_DOORS=false`; версия `1.1.0-release`, versionCode 4 и настройки подписи не менялись.
- В `.github/workflows/android.yml` добавлена проверка всех сгенерированных `BuildConfig`: поле должно присутствовать и иметь значение `false`; также проверяется наличие Debug и Release. Это предотвращает незаметное возвращение OpenDoors в новом варианте.
- README и `docs/BUILD_APK_RU.md` приведены в соответствие с кодом и уточняют, что речь о новых сборках; уже собранный APK не пересобирается этой правкой.
- APK, release tag, release workflow и данные помещений/маршрутов не изменялись. Новая сборка APK и публикация release не выполнялись.

## Проверки

**Локально успешно:**

- `python3 -m compileall -q tools`.
- `python3 tools/validate_indoor_data.py` — 2 этажа, 47 помещений, 101 узел, 105 рёбер, 3 закрытые области; проверены 34 официальных номера.
- `python3 tools/generate_kotlin_floor_maps.py --check` — Kotlin-карты соответствуют SVG.
- `python3 tools/extract_svg_geometry.py maps_and_design/floors/floor_1.svg --strict` и то же для `floor_2.svg` — успешно, `warnings=[]`.
- XML локализаций разобран; 630 ключей совпадают в `values`, `values-en`, `values-ru`; все 523 использования `R.string` из Kotlin разрешаются.
- Статическая проверка Gradle: ровно два `OPEN_DOORS`-поля, оба `false`; product flavors отсутствуют.
- Встроенная Python-проверка Android workflow с синтетическими `debug`, `release`, `testDebug` подтвердила успех при `false` и ожидаемо завершилась ошибкой для `release=true`.
- `git diff --check`.

**Локально недоступно; удалённый CI завершён:**

- `./gradlew --no-daemon testDebugUnitTest assembleDebug assembleRelease` локально не стартовал: в окружении отсутствуют `java`, `JAVA_HOME`, `ANDROID_HOME` и `ANDROID_SDK_ROOT`.
- GitHub Actions PR #27 выполнил Android unit tests, Debug/Release builds и проверку сгенерированных BuildConfig на head `694dc310f09e742f9d5d297686e2ad7bfb7ac2a3`: [`build` #43769509822](https://github.com/GoShadowS/logos-guide/actions/runs/43769509822/job/132982057609), [`build` #43769494299](https://github.com/GoShadowS/logos-guide/actions/runs/43769494299/job/132982008581), [`validate` #43769509846](https://github.com/GoShadowS/logos-guide/actions/runs/43769509846/job/132982057330), [`validate` #43769494238](https://github.com/GoShadowS/logos-guide/actions/runs/43769494238/job/132982008016) — все **success**. Это CI-проверка новых сборок, не runtime старого APK.

## Визуальная и runtime-проверка

- Эмулятор и устройство не запускались; визуального сравнения и скриншотов нет.
- Поведение `OPEN_DOORS=false` подтверждено статически по точкам входа в `MapScreen.kt`; runtime-проверка APK не проводилась.

## PR, итог и откат

- PR [#27](https://github.com/GoShadowS/logos-guide/pull/27) в `main` имеет статус **MERGED, squash**; GitHub `mergedAt=2026-11-15T22:35:06Z`.
- PR head: `694dc310f09e742f9d5d297686e2ad7bfb7ac2a3`. Итоговый squash SHA: `b221912ba9023a8ce3c049b1cd60631a05612c46`; его единственный parent — merge commit PR #26 `56981a0e6e3b79b8b2cd6d83ba9cebea9ce7e32f`.
- `build` и `validate` прошли; PR #27 не запускал release workflow, который запускается только при push тега `v*`.
- Откат: из актуального `main` выполнить `git revert b221912ba9023a8ce3c049b1cd60631a05612c46`, затем отправить revert отдельным PR. Это вернёт прежнее Release-значение `OPEN_DOORS=true` и снимет соответствующую CI-проверку/документацию. Уже собранный или опубликованный APK вручную не менять.
