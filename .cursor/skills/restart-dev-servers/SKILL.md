---
name: restart-dev-servers
description: >-
  Restarts the private-chat Spring Boot server and Vite dev client. Use when the
  user asks to restart, reload, or rerun the server and/or client, after backend
  or frontend changes, or when dev processes need a fresh start.
---

# Перезапуск сервера и клиента (private-chat)

## Когда применять

- Пользователь просит перезапустить сервер, клиент или оба
- После изменений в Kotlin/Spring или React, если нужен чистый dev-процесс
- Когда порты `8080` / `5173` заняты старым процессом

## Быстрый путь

Из корня репозитория:

```bash
chmod +x scripts/restart-dev.sh
./scripts/restart-dev.sh both    # сервер + клиент (по умолчанию)
./scripts/restart-dev.sh server  # только сервер
./scripts/restart-dev.sh client  # только клиент
```

Скрипт:

1. Останавливает процесс на порту (`8080` / `5173`)
2. Подхватывает `.env` из корня проекта
3. Выставляет `JAVA_HOME` через `~/.cursor/skills/gradle-java-home/scripts/resolve-java-home.sh`
4. Запускает `./gradlew :server:bootRun` и `npm run dev` в фоне
5. Ждёт health (`/actuator/health`) и ответ клиента

Логи и PID:

| Компонент | URL | Лог | PID |
|-----------|-----|-----|-----|
| Сервер | http://localhost:8080 | `$TMPDIR/private-chat-dev/server.log` | `server.pid` |
| Клиент | http://localhost:5173 | `$TMPDIR/private-chat-dev/client.log` | `client.pid` |

## Инструкции для агента

1. **Проверь терминалы** в папке terminals — если сервер/клиент уже запущены в foreground, предупреди, что скрипт убьёт процесс по порту.
2. **Запусти скрипт** с нужным режимом (`both` / `server` / `client`).
3. **Проверь результат**:
   - `curl -sf http://localhost:8080/actuator/health`
   - `curl -sf http://localhost:5173`
4. При ошибке — покажи последние строки лога (`tail -n 40`).

Не используй `pkill` по широким шаблонам (`java`, `vite`) — только скрипт или остановка по порту.

## Ручной перезапуск (если скрипт недоступен)

**Сервер:**

```bash
# из корня репозитория
export $(grep -v '^#' .env | xargs)   # если есть .env
export JAVA_HOME="$(~/.cursor/skills/gradle-java-home/scripts/resolve-java-home.sh)"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew :server:bootRun
```

**Клиент:**

```bash
cd client
npm run dev
# http://localhost:5173 — прокси /api → :8080
```

## Зависимости

Перед первым запуском сервера:

- PostgreSQL, Redis, NATS (см. README)
- `./gradlew flywayMigrate`
- `cp .env.example .env` и настройка секретов

Перед клиентом: `cd client && npm install` (Node.js 20+).
