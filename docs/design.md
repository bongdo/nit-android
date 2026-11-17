# nit-android: нативный клиент

Статус на 2026-11-17: MVP собран. В приложении есть вход, список чатов,
транскрипт со стримингом, markdown/таблицы/код/HTML-превью, вложения,
разрешения и формы, смена модели/агента, темы. Роль ассистента и оверлей
сознательно не входят в MVP; см. `decisions.md`. Реализация — один модуль
`:app`, а не шесть модулей из плана ниже.

Решение: Kotlin + Jetpack Compose, один `Activity`, чат — не WebView.
Бэкенд — существующий nit-gui; Android не говорит с OpenCode напрямую и не
хранит пароль OpenCode.

## Почему не оболочка

Capacitor/TWA — это WebView с мостом в Java. Чат остаётся HTML: свои скролл,
выделение текста, анимации, клавиатура. Нативные только ручки (шаринг, роль
ассистента). Для «native feel» это не подходит: системные жесты, предиктивный
back, IME и long-press меню всё равно ведут себя как страница.

## Стек

| Слой | Выбор | Почему |
| --- | --- | --- |
| UI | Jetpack Compose, Material 3 | системные компоненты, анимации, insets |
| Язык | Kotlin 2.2, `kotlinx.serialization` | без рефлексии, строгие DTO |
| Сеть | OkHttp 4 + `okhttp-sse` | SSE как в вебе, cookie jar, таймауты |
| Хранилище | DataStore + Room | настройки, кэш транскрипта, черновики |
| DI | вручную, без Hilt | один процесс, мало связей |
| Markdown | `org.jetbrains:markdown` (GFM) → Compose | контроль над таблицами и кодом |
| Код | `dev.snipme:highlights` | подсветка без WebView |
| Картинки | Coil 3 на общем OkHttp | куки и редиректы |
| HTML-превью | `WebView` в отдельном экране | документ, не интерфейс; JS без сети |
| Виджет | Glance | Compose для app widget |
| Тесты | JVM + Roborazzi | reducer и скриншоты без эмулятора |

`minSdk 26`, `targetSdk 36`, edge-to-edge обязателен.

## Экраны

| Экран | Поведение |
| --- | --- |
| Вход | адрес сервера, пароль, «Подключиться»; cookie в Keystore |
| Чаты | список по каталогам, поиск, running-dot; свайп — удалить с undo, long-press — меню |
| Чат | collapsing top bar, транскрипт, composer; на планшете — две панели |
| Транскрипт | markdown, таблицы с горизонтальным скроллом, код с копированием, tool calls свёрнуты |
| Действия | long-press: копировать, markdown, озвучить, поделиться, форк |
| Разрешения | карточка над composer или нотификация с Allow/Deny |
| Модель/агент | bottom sheet, а не выпадашка в шапке |
| Настройки | сервер, тема, размер текста, диктовка, TTS, уведомления |
| Ассистент | long-press питания → оверлей с микрофоном и живым транскриптом |
| Шаринг | `ACTION_SEND`/`ACTION_PROCESS_TEXT` → лист «новый чат / в текущий» |
| Виджет и плитка | «Спросить nit», последние три чата |

Чего нет: левой рейки с hover, `⌘K`, свайпа от края, 13 веб-тем. Это веб-идиомы;
нативные — drawer/list-detail, поиск-экран, Material You.

## Native feel — конкретные вещи

- `enableEdgeToEdge()` + `safeDrawing` + `imePadding()`: composer едет с
  клавиатурой без прыжка.
- Предиктивный back: `enableOnBackInvokedCallback`, `PredictiveBackHandler` в
  drawer, sheet и превью.
- `ModalBottomSheet` с ручкой вместо центрированных диалогов.
- `SharedTransitionLayout` из строки сессии в чат, 250–300 мс.
- Нативное выделение текста (`SelectionContainer`), ссылки — Custom Tabs.
- Haptics на отправку, стоп, push-to-talk, разрешение.
- `LazyColumn` со стабильными ключами и `contentType`: 120 Гц, стриминг без
  перестроения всего списка. Автоскролл уступает руке, кнопка «вниз».
- Skeleton вместо спиннера на первой загрузке.
- SplashScreen API, тема и иконка adaptive.
- Нотификация хода: канал, «Стоп», inline reply; foreground service только пока
  идёт ход.
- Роль ассистента: `VoiceInteractionService` + `RecognitionService` + сессия,
  как в `../nita/web/android`, но сессия открывает Compose-оверлей, а не Activity
  с WebView.
- `ACTION_SEND`, динамические shortcuts (последние сессии), deep link
  `nit://s/<id>`, `TileService`, Glance-виджет.
- TalkBack, масштаб шрифта, цели 48dp.
- Планшет/складной: `ListDetailPaneScaffold`, окна, клавиатура.

## Рендеринг

- Markdown парсится в AST и рисуется Compose-нодами; таблица — горизонтальный
  скролл, код — `highlights` + копирование в snackbar.
- HTML-блок и HTML-документ открываются на отдельном экране в `WebView`:
  JS включён (нужны графики), сеть и файлы запрещены, `addJavascriptInterface`
  нет, тёмный фон, «открыть в браузере» отсутствует.
- Длинные ответы: рендер по мере стриминга, без повторного парсинга всего текста
  на каждый дельта-кусок (кэш по message/part).
- Diff: нативный список строк, monospace, горизонтальный скролл.

## Данные и API

Android использует `/api/*` nit-gui, не OpenCode:

- `POST /api/login` (новый) — JSON `{password}` → та же cookie `nit_gate`.
  Второй механизм токенов не нужен; cookie уже HttpOnly и подписана.
- `GET /api/sessions?dir=`, `GET /api/sessions/{id}/messages?limit=50&before=`
  — постранично, новые сверху догружаются вверх.
- `GET /api/events` — нормализованные события `session.text.delta`,
  `session.tool.*`, `session.step.*`; тот же прокси, что у веба.
- `POST /api/sessions/{id}/prompt`, `/interrupt`, `/permissions/...`,
  `/form/...`, `/upload`, `/model`, `/agent`.
- TTS/STT: на телефоне системные `TextToSpeech` и `SpeechRecognizer` лучше
  серверных; серверные остаются запасным путём и для качества GigaAM.

Reducer стрима — порт `web/src/lib/stream.ts`. Чтобы реализации не разъехались:
записанные JSONL-фикстуры событий лежат в репозитории и прогоняются в JVM-тестах
обеих сторон (в вебе — тот же файл).

Кэш: список сессий, последние ~200 сообщений на чат, черновики, настройки.
Отправка офлайн не ставится в очередь молча — только явный retry.

## Безопасность

- Пароль и cookie не в открытом виде: Keystore.
- Только HTTPS; cleartext запрещён (`networkSecurityConfig`).
- Cookie живёт 7 дней; для телефона — 30 дней со скользящим продлением, отзыв
  через `bin/auth -force`.
- Логин: тот же rate limit, что у веба.
- WebView-превью: без сети, без файлов, без моста.
- APK: подпись, `allowBackup=false` для секретов, обновления только с известного
  адреса.

## Сборка

На этой машине (aarch64) официального `aapt2` нет, локальный APK не собрать.
Варианты: x86_64 `nixos` (сейчас offline), GitHub Actions `ubuntu-latest`
(в nita уже есть workflow), либо Termux-патч `aapt2` как костыль.

`bin/check`: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`.
Скриншоты — Roborazzi в JVM, без эмулятора.
Выдача APK — через `drop` в nit-gui и установка по ссылке; Play не обязателен.

## План работ

P0, последовательно (контракт, дальше не трогается):

1. Gradle-скелет модулей, `bin/check`, CI.
2. DTO + записанные фикстуры событий.
3. дизайн-токены, темы, типографика.
4. экран входа и `POST /api/login` в nit-gui.

Дальше параллельно, у каждого потока свои модули:

| Поток | Модули | Результат |
| --- | --- | --- |
| A | `core:network`, `core:data` | логин, сессии, SSE, reducer, Room |
| B | `core:design`, `feature:transcript` | markdown, код, таблицы, diff, HTML-превью |
| C | `feature:composer` | ввод, вложения, диктовка, TTS, черновики |
| D | `feature:sessions`, `feature:settings` | список, поиск, навигация, настройки |
| E | `feature:assistant`, `app` | роль ассистента, шаринг, плитка, виджет, нотификации |
| F | nit-gui (Go) | JSON-логин, TTL cookie, хук в ntfy, тесты |

Слияние: `core:model` и `core:design` заморожены после P0; каждый поток владеет
своими файлами; интеграция — раз в день на зелёном `bin/check`.

Реалистично: 2–3 недели до ежедневно пригодной версии, ещё неделя на полировку.
Узкое место — не количество агентов, а контракт и ревью диффов.

## Риски

- Паритет markdown с вебом — самая дорогая часть; тесты на реальных ответах.
- Два reducer'а (TS и Kotlin) разъедутся без фикстур.
- nit-gui меняется; Android должен оставаться тонким клиентом без своей логики.
- Роль ассистента: политика Play, различия прошивок, нет hotword.
- Пуши: SSE живёт только при открытом приложении. FCM — 2–3 дня и Google;
  ntfy — дешевле и без Google, но inline-actions беднее.
