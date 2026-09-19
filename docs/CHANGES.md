# XENO v1.0.0-mali — Release Notes

## Что нового в v1.0.0-mali

### 🔧 Критические фиксы
- **Фикс краша при запуске** — `UnsatisfiedLinkError: libxeno-jni.so not found` устранён. JNI-глай теперь собирается под правильным именем (`libxeno-jni.so`), соответствующим `System.loadLibrary("xeno-jni")`.
- **Slim APK (~38 MB вместо 138 MB)** — ядро (`libxeno-core.so`) исключено из APK, скачивается отдельно при первом запуске (Winlator-style). Апк стал в 3.5 раза легче.
- **Ядро в релизе** — `libxeno-core.so` выложено как ассет релиза `v1.0.0-mali`, `CoreRepository` находит и качает его автоматически.

### 🎨 Ренейм и брендинг
- Пакет: `com.armsx3` → `com.xeno.emulator`
- Название: ARMSX3 → XENO
- Иконка, заставка, лэйблы обновлены
- Версия: `1.0.0-mali`, versionCode 100

### 🎮 Mali GPU auto-tune
- Автоматический пресет производительности под Mali GPU (DeviceTier / MaliGpuInfo / MaliOptimizer)
- Доступен в **PerformanceTab** (overlay в игре): один тап — оптимальные настройки для твоего чипа

### 🔗 Discord Rich Presence
- Мост переписан под `libxeno_discord.so`, JNI-символы под `com.xeno.discord.DiscordNative`
- Работает в отдельном процессе (лицензионная изоляция)

### ☁️ WebDAV пресеты для синка сейвов
- Готовые one-tap пресеты:
  - **pCloud** — `https://webdav.pcloud.com/`
  - **Mail.ru Cloud** — `https://webdav.cloud.mail.ru/`
  - **Яндекс.Диск** — `https://webdav.yandex.ru/`
- Basic-auth (логин / app-password), подсказки в UI
- Google Drive / Dropbox / GoFile — отмечены как API-only (нужен dev key)

### 🏗️ Сборка и CI
- Docker ARM64 APK на GitHub Actions (кэш LLVM + Gradle)
- Релиз создаётся автоматически на тегах `v*`
- Play AAB — отдельный скрипт `build-play-aab.sh`

---

## Миграция с 0.9.x
- **Ядро не совместимо** — старый `libarmsx3-core.so` не подойдёт. При первом запуске скачается новое `libxeno-core.so` автоматически.
- Настройки переносятся (SharedPreferences).
- WebDAV настройки — переносятся, пресеты доступны сразу.

---

## Известные ограничения
- **FTP / SMB / SFTP / Nextcloud / OwnCloud** — нет отдельных транспортов, используйте WebDAV (все популярные NAS/облака его отдают).
- **Авто-синк сейвов** — пока ручной (тап в меню), авто-синк в планах.
- **Кроссплатформенный профиль** (Android ↔ PC) — в разработке.