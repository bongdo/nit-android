# nit-android

- `bin/check` — unit tests, lint и `assembleDebug`. Единственная команда проверки; она
  должна быть зелёной до отчёта и публикации APK.
- `bin/apk` — копирует debug APK в `dist/nit-debug.apk` и печатает путь.
- `bin/java-home` — находит JDK 17 (nix store, `JAVA_HOME` или `java` в PATH).
- `tools/aapt2` — обёртка над x86_64 aapt2 через qemu на aarch64. Не заменять на
  копию бинарника.
- Комментарии в коде запрещены. Разрешены машинные директивы и `shortcut:`.
- Новые зависимости — только когда stdlib и Compose не хватает; объяснять в README.
- Интерфейс не показывает размышления и пути. Инструменты — свёрнутый список.
- Пароль не хранится; только cookie сессии в Keystore.
- После зелёного `bin/check` — `save "..."`.
- Меняешь контракт API — сверяйся с `../nit-gui/web/src/lib/types.ts` и
  `internal/server/server.go`, не угадывай поля.
