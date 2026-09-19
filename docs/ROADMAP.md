# XENO — Roadmap

## v1.1 — Cloud Sync MVP (Q4 2026)
**Цель:** «Поставил игру — сейвы синкаются везде».

### Must-have
1. **Google Drive API** (OAuth, `Drive API v3`)
   - Файл `saves/<title_id>/<slot>.sav` в скрытой папке приложения
   - Ручной тап «Синхронизировать сейчас» + фоновый `WorkManager` при сохранении/загрузке
   - Конфликт-резолв: «локальный новее / облачный новее / показать обе версии»

2. **Авто-синк** (опционально, дефолт off)
   - `WorkManager` каждые 15 мин + триггер на `onPause`/`onResume` игры
   - Индикатор в тулбаре: ☁️ синкается / ✅ синхронизировано / ⚠️ конфликт

### Nice-to-have
- **Dropbox / OneDrive API** — те же эндпойнты, другой OAuth клиент
- **Экспорт/импорт профиля** — JSON со всеми настройками + патчами + шейдерами

---

## v1.2 — P2P & Cross-platform (Q1 2027)
**Цель:** «Свой сервер / без облака / Android ↔ PC».

1. **Syncthing SDK** (Go-биндинг или `syncthing-android`)
   - Прямой телефон ↔ ПК / телефон ↔ телефон
   - QR-код паринг: сканируешь на ПК — папка `saves/` появляется в Syncthing
   - Работает в LAN и через реле (NAT traversal встроен)

2. **Кроссплатформенный профиль** (`xeno-profile.json`)
   - Единый формат: settings + saves + patches + shaders + controller maps
   - SHA256 каждого файла, мерж по mtime/content-hash
   - RPCS3 (Windows/Linux/macOS) читает/пишет тот же формат

---

## v1.3 — Social & Power-user (Q2 2027)
1. **Discord Rich Presence + Share Save** — «Кинуть сейв другу» из оверлея
2. **Версионирование сейвов** — история (последние 10 версий), откат
3. **Облачные конфиги пер-гейм** — пулл-реквесты в репу community-configs, подтягиваются автоматически
3. **WebRTC прямой обмен** — кинуть сейв/ISO другу за секунды без загрузки в облако

---

## Не делаем (явно)
- Отдельные SMB / SFTP / FTP / Nextcloud / OwnCloud транспорты — WebDAV уже закрывает 95 % кейсов.
- FTP — deprecated.
- Собственный облачный бэкенд — используем готовые (Drive, Syncthing, WebDAV).

---

## Технический долг (параллельно)
- Котлин 2.x migration (уже в процессе)
- Compose UI → Material3 полноценно
- Тесты: UI (Compose testing), Unit (CoreRepository, RPCSX), Instrumented (WebDAV mock)
- Документация: `CONTRIBUTING.md`, `ARCHITECTURE.md`, ADR для ключевых решений