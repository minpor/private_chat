# private-chat

Приватный мессенджер — бэкенд (Kotlin, Spring Boot 4 WebFlux).

## Стек

- Spring Boot **4.0.6** WebFlux, Kotlin **2.4**, JDK **21**
- PostgreSQL (R2DBC), Redis (reactive), NATS JetStream
- Draft-sync через WebSocket + REST fallback; JWT auth; outbox → NATS → `message.new`

Архитектура: [ARCHITECTURE.md](ARCHITECTURE.md) · полный план: [docs/PLAN.md](docs/PLAN.md)

## Что установить

| Компонент | Версия | Зачем |
|-----------|--------|-------|
| JDK | 21 LTS | runtime, Gradle toolchain |
| PostgreSQL | 16+ | users, chats, messages, outbox |
| Redis | 7+ | drafts, rate limit |
| NATS | 2.10+ с `-js` | outbox fan-out |

Примеры (Ubuntu / Debian):

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk postgresql redis-server nats-server
```

### NATS (apt, с JetStream)

Пакет из репозитория Ubuntu (2.10.x) подходит для проекта. После установки включите JetStream в конфиге:

```bash
sudo apt-get install -y nats-server
```

Отредактируйте `/etc/nats-server.conf` — добавьте (или раскомментируйте) блоки:

```
host: 127.0.0.1
port: 4222
http_port: 8222

jetstream {
  store_dir: "/var/lib/nats/jetstream"
}
```

Создайте каталог для JetStream и запустите сервис:

```bash
sudo mkdir -p /var/lib/nats/jetstream
sudo chown nats:nats /var/lib/nats/jetstream
sudo systemctl enable nats-server
sudo systemctl restart nats-server
```

Проверка:

```bash
systemctl status nats-server
curl -s http://localhost:8222/healthz
# ожидается: {"status":"ok"}
```

В `.env` проекта: `NATS_URL=nats://localhost:4222`.

Альтернатива без systemd — запуск вручную: `nats-server -js -sd /var/lib/nats/jetstream -p 4222 -m 8222`.

## Подготовка БД

```sql
CREATE USER private_chat WITH PASSWORD 'your_password';
CREATE DATABASE private_chat OWNER private_chat;
```

Redis: `redis-server` на порту 6379 (обычно стартует автоматически после `apt-get install redis-server`).

## Конфигурация

```bash
cp .env.example .env
# отредактировать JWT_SECRET (минимум 32 символа), DB_PASSWORD и т.д.
export $(grep -v '^#' .env | xargs)
```

## Сборка и запуск

```bash
# миграции (Flyway, вне runtime — blocking JDBC только здесь)
./gradlew flywayMigrate

# сервер
./gradlew :server:bootRun
```

Health: `GET http://localhost:8080/actuator/health`

Метрики (Prometheus): `GET http://localhost:8080/actuator/prometheus`

Structured logging (JSON): `SPRING_PROFILES_ACTIVE=json ./gradlew :server:bootRun`

Документация из KDoc:

```bash
./gradlew :server:dokkaHtml
# server/build/dokka/html/index.html
```

## Проверка зависимостей

```bash
psql -h localhost -U private_chat -d private_chat -c 'SELECT 1'
redis-cli ping
curl -s http://localhost:8222/healthz
systemctl is-active nats-server
```

## Пример API (curl)

```bash
# регистрация
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"alice","password":"password123","displayName":"Alice"}'

# логин (сохраните accessToken)
curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"bob","password":"password123","displayName":"Bob"}'

TOKEN="<accessToken>"

# direct-чат с bob
curl -s -X POST http://localhost:8080/api/v1/chats \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"username":"bob"}'

CHAT_ID="<chat id from response>"

# отправить сообщение
curl -s -X POST "http://localhost:8080/api/v1/chats/$CHAT_ID/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"clientMessageId":"550e8400-e29b-41d4-a716-446655440000","text":"Привет!"}'

# история
curl -s "http://localhost:8080/api/v1/chats/$CHAT_ID/messages?limit=50" \
  -H "Authorization: Bearer $TOKEN"

# draft-sync (REST fallback)
DRAFT_ID="660e8400-e29b-41d4-a716-446655440001"
curl -s -X POST "http://localhost:8080/api/v1/chats/$CHAT_ID/drafts" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"draftId\":\"$DRAFT_ID\",\"clientSessionId\":\"cli-1\"}"

curl -s -X PUT "http://localhost:8080/api/v1/chats/$CHAT_ID/drafts/$DRAFT_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"revision":1,"text":"Привет через draft!"}'

curl -s -X POST "http://localhost:8080/api/v1/chats/$CHAT_ID/drafts/$DRAFT_ID/commit" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"clientMessageId":"770e8400-e29b-41d4-a716-446655440002","expectedRevision":1}'
```

### WebSocket draft-sync

Подключение: `ws://localhost:8080/api/v1/ws?token=<accessToken>`

Фреймы (JSON text):

```json
{"type":"draft.start","payload":{"chatId":"<uuid>","draftId":"<uuid>","clientSessionId":"cli-1"}}
{"type":"draft.patch","payload":{"chatId":"<uuid>","draftId":"<uuid>","revision":1,"text":"Привет"}}
{"type":"draft.commit","payload":{"chatId":"<uuid>","draftId":"<uuid>","clientMessageId":"<uuid>","expectedRevision":1}}
```

Ответы сервера: `draft.started`, `draft.patch.ack`, `message.accepted`, `message.new` (всем участникам чата).

## Read replica (опционально)

Для разгрузки primary PostgreSQL чтение истории (`GET /messages`) можно направить на replica:

```bash
DB_READ_REPLICA_ENABLED=true
DB_READ_REPLICA_HOST=localhost
DB_READ_REPLICA_PORT=5433
# DB_READ_REPLICA_USER / DB_READ_REPLICA_PASSWORD — если отличаются от primary
```

Запись, идемпотентность и транзакции всегда идут на primary (`DB_HOST`).

## Нагрузочный тест (k6)

Цель фазы 3: **2k TPS** на `POST /messages`. Перед прогоном поднимите rate limit:

```bash
export MESSAGE_RATE_LIMIT=200000
export $(grep -v '^#' .env | xargs)
./gradlew :server:bootRun
```

В другом терминале:

```bash
# установка: https://grafana.com/docs/k6/latest/set-up/install-k6/
k6 run load-tests/message-write.k6.js
```

Параметры:

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `BASE_URL` | `http://localhost:8080` | URL сервера |
| `TARGET_TPS` | `2000` | Целевой RPS |
| `DURATION` | `2m` | Длительность сценария |
| `LOAD_TEST_TOKEN` / `LOAD_TEST_CHAT_ID` | — | Пропустить setup, использовать готовые |

## Gradle-задачи

| Задача | Описание |
|--------|----------|
| `./gradlew :server:bootRun` | Запуск сервера |
| `./gradlew flywayMigrate` | SQL-миграции |
| `./gradlew :server:detekt` | Статический анализ |
| `./gradlew :server:test` | Тесты + ArchUnit |
| `./gradlew :server:dokkaHtml` | HTML из KDoc |

## Типичные ошибки

- **Connection refused (PostgreSQL/Redis/NATS)** — сервис не запущен или неверный host/port в `.env`
- **Flyway migrate failed** — БД не создана или неверные `DB_*`
- **JWT secret too short** — `JWT_SECRET` минимум 32 символа для HMAC-SHA256
- **NATS health DOWN** — JetStream не включён в `/etc/nats-server.conf`, сервис не запущен (`systemctl status nats-server`) или неверный `NATS_URL`
