# Worker & HR — лабораторная работа №2 (SOA)

Два JAX-RS-сервиса по спецификациям из `docs/openapi/` и веб-клиент на React.

| Сервис | Сервер | Контекст | Что делает |
|---|---|---|---|
| Worker (`worker-service`) | WildFly 35 | `/api/workers…` | Коллекция работников в PostgreSQL: CRUD, сортировка, фильтрация, пагинация, организации, статистика. Также отдаёт веб-клиент (`/`) и Swagger UI (`/docs/`). |
| HR (`hr-service`) | Payara 6 | `/hr/…` | `move` вызывает Worker API по HTTPS (JAX-RS Client); `fire` отвечает `307` на `PATCH /api/workers/{id}?status=FIRED`. |

Спецификации `docs/openapi/*.yaml` — источник правды; код им подчиняется.

## Структура

```
docs/openapi/            спецификации OpenAPI + Swagger UI
worker-service/lab/worker/
  controller/            JAX-RS ресурсы: только разбор параметров
  service/               бизнес-логика, транзакции (@Transactional)
  repository/            JPA (EntityManager), Criteria API для фильтров/сортировки
  model/                 JPA-сущности Worker, Organization и @Embeddable-части
  dto/                   records запросов/ответов + Bean Validation
  mapper/                entity ↔ dto
  query/                 разбор filter=field:op:value и sort=field:asc|desc
  validation/            параметры URL и JSON из query: 400 (формат) vs 422 (значение)
  exception/             ApiException и ExceptionMapper'ы → тело ошибки по спецификации
  config/                Jackson, CORS, запрет тела запроса (415), справочник организаций
hr-service/lab/hr/
  controller/ service/ client/ dto/ exception/ config/ validation/
frontend/                веб-клиент (React + Vite), собирается в worker.war
docker/                  локальный стенд: PostgreSQL + WildFly + Payara
deploy/                  развёртывание на helios
```

Сервисы независимы: общего модуля нет, мелкие классы (ошибки, Jackson, CORS) намеренно продублированы.

## Сборка

Нужны JDK 17 (toolchain; Gradle может работать на 21) и Node.js с npm.

```sh
./gradlew build      # React + worker.war + hr.war + unit-тесты
./gradlew test       # только unit-тесты
```

Результат: `worker-service/build/libs/worker.war`, `hr-service/build/libs/hr.war`.

## Локальный запуск (Docker)

Образы собираются из тех же дистрибутивов WildFly/Payara, что ставятся на helios. Их нужно один раз скачать в `.local/dist/`:

```sh
mkdir -p .local/dist && cd .local/dist
curl -fLo wildfly.zip https://repository.jboss.org/nexus/repository/releases/org/wildfly/wildfly-dist/35.0.1.Final/wildfly-dist-35.0.1.Final.zip
curl -fLo payara.zip https://repo.maven.apache.org/maven2/fish/payara/distributions/payara/6.2025.11/payara-6.2025.11.zip
curl -fLo postgresql.jar https://repo.maven.apache.org/maven2/org/postgresql/postgresql/42.7.5/postgresql-42.7.5.jar
shasum -a 256 wildfly.zip payara.zip
# 1e4ac11ac3970ae6a73156dd11b88ce5243220d96ab346498c88c562262a62ac  wildfly.zip
# 4f8248f1fc2cedf14a829dbec73768bc824c2c418d60843c30a365a6ef76f486  payara.zip
```

```sh
bash docker/init.sh               # docker/.env со случайными паролями + сертификаты в .local/docker/tls
./gradlew build
docker compose -f docker/compose.yaml up -d --build
bash docker/deploy.sh             # горячий передеплой после новой сборки
docker compose -f docker/compose.yaml down        # остановка (данные БД сохраняются в volume)
```

| Компонент | Адрес |
|---|---|
| Веб-клиент и Worker API | https://localhost:8543 |
| Swagger UI | https://localhost:8543/docs/ |
| HR API | https://localhost:18081 |
| PostgreSQL | 127.0.0.1:15432, БД `soa_lab2`, пользователь `soa`, пароль — `SOA_DB_PASSWORD` из `docker/.env` |

Доступ только по HTTPS: HTTP-listener WildFly удалён, `http-listener-1` Payara отключён. Чтобы браузер/Postman доверяли сертификатам, добавьте `.local/docker/tls/worker.crt` и `hr.crt` в доверенные (Keychain / настройки Postman → Certificates → CA certificates). Перевыпуск сертификатов: `bash docker/init.sh --certs`, затем `docker compose -f docker/compose.yaml restart`.

## Тестирование

- Unit-тесты: `./gradlew test` — разбор фильтров/сортировок, строгий разбор JSON и разделение 400/422, логика HR.
- API: `postman/soa-lab2.postman_collection.json` — 54 запроса, 235 проверок. Кроме сценарных проверок, каждый ответ сверяется со спецификацией: статус должен быть описан у операции, тело — соответствовать её JSON-схеме (`additionalProperties: false`, `required`, `nullable`, enum, форматы дат).
- Коллекция генерируется из `docs/openapi/*.yaml`; после изменения спецификаций или сценариев: `cd postman && npm install && npm run build`.

Запуск коллекции из консоли (стенд должен быть поднят):

```sh
cat .local/docker/tls/worker.crt .local/docker/tls/hr.crt > /tmp/soa-ca.pem
NODE_OPTIONS=--use-openssl-ca SSL_CERT_FILE=/tmp/soa-ca.pem \
  npx newman@6 run postman/soa-lab2.postman_collection.json --timeout-request 15000
```

## Конфигурация сервисов

Системные свойства JVM (или переменные окружения `WORKER_BASE_URL` и т. п.):

| Свойство | Сервис | Назначение |
|---|---|---|
| `app.cors.origin` | оба | Разрешённые origin веб-клиента через запятую |
| `app.cors.allow.hr.redirect` | Worker | Разрешить CORS `Origin: null` для `PATCH …?status=FIRED` после 307 от HR |
| `worker.base.url` | HR | Адрес Worker API для вызовов HR |
| `worker.public.base.url` | HR | Адрес Worker для заголовка `Location` в ответе `fire` |
| `worker.truststore` | HR | PKCS12 с сертификатом Worker |
| `WORKER_TRUSTSTORE_PASSWORD` (только переменная окружения) | HR | Пароль этого truststore |

Worker берёт БД из datasource `java:/jdbc/WorkerDS` (настраивается в WildFly); таблицы создаёт Hibernate, справочник организаций (10, 20, 30) заполняется при старте.

## Развёртывание на helios

Сервер: `helios.cs.ifmo.ru:2222`, установка в `~/soa-lab2`, БД `studs` на `pg:5432` (схема пользователя, пароль из `~/.pgpass`).

| Скрипт | Где запускается | Что делает |
|---|---|---|
| `deploy/deploy-helios.sh` | локально | Загружает собранные WAR, драйвер PostgreSQL и `deploy/server/*` на helios, затем `stop.sh` → `setup.sh` → `start.sh`; скачивает сертификаты в `.local/helios/` |
| `deploy/tunnel-helios.sh` | локально | SSH-туннель: `https://localhost:18443` → Worker + UI, `https://localhost:19443` → HR |
| `deploy/server/setup.sh` | helios | Один раз ставит WildFly 35.0.1 и Payara 6.2025.11 (с проверкой SHA-256), генерирует сертификаты, создаёт домен Payara; каждый раз переписывает конфигурацию через `Configure.java` |
| `deploy/server/Configure.java` | helios | Правит `standalone.xml` и `domain.xml`: HTTPS с нашими сертификатами, отключение HTTP, datasource `java:/jdbc/WorkerDS`, адреса и CORS; подставляет адреса helios в `config.json` и спецификации внутри `worker.war` |
| `deploy/server/start.sh`, `stop.sh` | helios | Запуск/остановка серверов и деплой WAR |

```sh
./gradlew build
bash deploy/deploy-helios.sh      # SSH-ключ: SOA_SSH_KEY (по умолчанию ~/.ssh/helios_ed25519)
bash deploy/tunnel-helios.sh      # держать открытым, пока работаете в браузере
```

Порты на helios: Worker HTTPS 18443 (WildFly со смещением портов +10000, management 127.0.0.1:19990), HR HTTPS 19443, администрирование Payara 127.0.0.1:19448. HTTP-слушатели отключены. Для доверия браузера добавьте `.local/helios/worker.crt` и `hr.crt` в Keychain (Always Trust).
