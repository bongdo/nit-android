# nit

Нативный Android-клиент к `nit-gui`. Один экран: список чатов в шторке снизу,
транскрипт, композер. Своего бэкенда нет: приложение говорит с существующим
Go-прокси `nit-gui`, который уже держит сессии OpenCode, загрузки, SSE и гейт.

Пакет: `space.bongdo.nit`. `minSdk 26`, `targetSdk 35`. Kotlin 2.0, Compose
Material 3.

## Что уже есть

- Вход по имени и паролю через существующую страницу `/login` гейта. Кука
  сессии хранится в `EncryptedSharedPreferences` (Android Keystore). Код 2FA
  вводится там же, если он включён.
- Список чатов по каталогам, серверный поиск, индикатор running.
- Транскрипт: markdown, таблицы, код с копированием, HTML-превью в песочнице
  без сети, вложения, ошибки шага.
- Стриминг через `/api/events` (SSE), остановка, догрузка старых сообщений.
- Композер: текст, файлы (изображения/PDF/что угодно), отправка, стоп.
- Карточки разрешений и форм, чтобы ход не вставал на вопросе агента.
- Смена модели и агента, тема (system/dark/light), размер текста.
- Сборка debug APK: `bin/apk`.

Чего нет в этой версии: роли ассистента и оверлея, push-уведомлений, офлайн-кэша
и очереди, TTS/STT, дерева субагентов, экрана инстансов, 13 тем, share intent.
Это следующие волны, не обещания.

## Сборка

```sh
bin/check     # unit tests + lint + assembleDebug
bin/apk       # copy the APK to dist/nit-debug.apk and print the path
```

`bin/check` сам находит JDK 17 в nix store. Android SDK ожидается в
`~/Android/Sdk` (или `ANDROID_HOME`).

На aarch64 официальный `aapt2` из build-tools собран под x86_64. Поэтому
`tools/aapt2` запускает его через `qemu-x86_64` с x86_64 glibc из nix store;
Gradle получает этот файл через `-Pandroid.aapt2FromMavenOverride`. На x86_64
обёртка просто передаёт вызов настоящему бинарнику.

## Сервер

В приложении адрес вводится целиком, например `https://bongdo.space/nit`.
Логин уходит на `origin/login` того же хоста, API — на `origin/nit/api/*`.
Пароль проверяет сервер; приложение не хранит пароль, только cookie.

Если сервер за reverse proxy с другим префиксом, достаточно поменять адрес:
логин всегда берётся от корня хоста, API — от пути.

## Архитектура

```
MainActivity
  AppViewModel        состояние, SSE, действия
    NitApi            OkHttp + cookie jar, JSON
    EventStream       /api/events, переподключение
    StreamReducer     события OpenCode → Message
    SettingsStore     DataStore
    SecureStore       EncryptedSharedPreferences
  ui/                 Compose-экраны
```

Один модуль `:app`. Для MVP этого достаточно; разделение на `core:*` и
`feature:*` имеет смысл, когда появятся независимые волны (виджет, офлайн, TTS).

## Проверки

- `app/src/test` — reducer стрима и разбор адреса сервера.
- Ручной сценарий: вход → список чатов → новый чат в папке → ответ с таблицей
  и HTML → вложение → разрешение → смена модели → выход.
