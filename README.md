# Homework 5: Currency Rate Services

Два сервиса на Java с использованием Spring Boot и gRPC.

## Структура проекта

- **currency-api** — общий модуль с gRPC-определениями (proto)
- **currency-rate-provider** — сервис 1: сервер, возвращающий курс USDRUB
- **rate-printer** — сервис 2: клиент, выводящий курс каждые 5 секунд

## Требования

- Java 11+
- Maven 3.6+
- Docker (для ZooKeeper и Pact Broker)
- Apache ZooKeeper — через Docker (см. ниже) или установленный отдельно

## Twelve-Factor App (доработка по домашке)

| Фактор | Статус | Что сделано в репозитории |
|--------|--------|---------------------------|
| **V. Сборка, релиз, выполнение** | ✅ | **Сборка** — отдельно: `mvn package` или `docker build -f Dockerfile…` (multi-stage, Maven в build-стадии). **Релиз** — тегованный образ с JAR (`*:local` в примерах). **Выполнение** — только `docker compose … up` по `docker-compose-apps.yml` (без секции `build:`) или `java -jar` на хосте. |
| **IX. Одноразовость** | ✅ | **Graceful shutdown:** `server.shutdown=graceful`, `spring.lifecycle.timeout-per-shutdown-phase=30s`; на провайдере дополнительно `grpc.server.shutdown-grace-period=30s` (завершение активных gRPC после SIGTERM). |
| **X. Паритет dev / prod** | ✅ | Профиль **`docker`**: `application-docker.properties` — адреса ZooKeeper, порты и `instance-host` из **переменных окружения** (те же ключи, что и локально, другие значения). Запуск в Docker: `SPRING_PROFILES_ACTIVE=docker` (см. `docker-compose-apps.yml`). Локально без профиля — прежний `application.properties`. |
| **XI. Журналирование** | ✅ | `logback-spring.xml` подключает только **`base.xml`** Spring Boot → логи **в stdout**, без файловых appenders. Прикладные логи (gRPC/REST, версия) — туда же. |

Ниже — **по порядку факторов** из таблицы: что сделано в репозитории и **как проверить** (типовой сценарий через Docker из корня `~/software-design`). Порты **2181 / 8080 / 8082 / 9090** на хосте должны быть свободны (`docker compose -f docker-compose-apps.yml down` и при необходимости `ss -tlnp` / `sudo ss -tlnp`).

### V. Сборка, релиз, выполнение

**Реализация:** multi-stage **`Dockerfile.currency-rate-provider`** и **`Dockerfile.rate-printer`** (Maven в build-стадии, в runtime — JRE + JAR). В **`docker-compose-apps.yml`** у приложений только **`image:`** (`currency-rate-provider:local`, `rate-printer:local`), **без** секции **`build:`** — сборка образов и запуск контейнеров разведены.

**Как проверить**

1. Сборка образов (стадия **сборки/релиза**):

   ```bash
   cd ~/software-design
   docker build -f Dockerfile.currency-rate-provider -t currency-rate-provider:local .
   docker build -f Dockerfile.rate-printer -t rate-printer:local .
   ```

2. Запуск без пересборки в compose (стадия **выполнения**): `docker compose -f docker-compose-apps.yml up -d` — в выводе не должно быть долгого `mvn` для этих сервисов.

3. Образы: `docker images | grep -E 'currency-rate-provider|rate-printer'` — есть тег **`local`**.

4. Контейнеры: `docker compose -f docker-compose-apps.yml ps` — **Up**.

5. Сервис отвечает: подождать **10–30 с** после первого **`up`**, затем `curl -sS -w '\nHTTP %{http_code}\n' http://127.0.0.1:8080/actuator/health` и то же для **`:8082`** — **`{"status":"UP"}`**, код **200**. (Сразу после старта возможен обрыв соединения — JVM ещё поднимает Tomcat. Если через **`localhost`** пусто — **`127.0.0.1`** или **`curl -4`**: часто **`localhost` → IPv6**, а проброс Docker — IPv4.)

6. Дополнительно: `docker compose -f docker-compose-apps.yml rm -sf currency-rate-provider`, затем `docker rmi currency-rate-provider:local`, снова **`docker compose -f docker-compose-apps.yml up -d`** — сервис **currency-rate-provider** не поднимется: Docker попытается **pull** образа с хаба и получит отказ (**`pull access denied`** / репозиторий не существует). Compose **не запускает Maven-сборку**; без локального тега **`local`** релиза нет.

Локально без Docker: **`mvn package`** и **`java -jar`** — см. раздел **«Запуск»** ниже.

### IX. Одноразовость (корректное завершение)

**Реализация:** в **`application.properties`** обоих модулей — **`server.shutdown=graceful`**, **`spring.lifecycle.timeout-per-shutdown-phase=30s`**; у провайдера ещё **`grpc.server.shutdown-grace-period=30s`**.

**Как проверить** (нужен поднятый стек). У **`docker stop`** по умолчанию **10 с** до SIGKILL — меньше **30 с** фазы shutdown, поэтому используйте **`-t 45`**.

1. Терминал 1: `docker compose -f docker-compose-apps.yml logs -f currency-rate-provider`
2. Терминал 2: `docker compose -f docker-compose-apps.yml stop -t 45 currency-rate-provider`
3. В логах терминала 1 — строки в духе **`Completed gRPC server shutdown`**, **`Commencing graceful shutdown…`**, **`Graceful shutdown complete`**, выход контейнера с **кодом 0**.

Повторить при желании для **`rate-printer`**: **`logs -f rate-printer`** и **`stop -t 45 rate-printer`**. Затем снова **`docker compose -f docker-compose-apps.yml up -d`**.

### X. Паритет разработки / эксплуатации

**Реализация:** профиль **`docker`** — файлы **`application-docker.properties`** в **`currency-rate-provider`** и **`rate-printer`** (те же ключи, что в обычном конфиге, значения из **`${…}`** / env). В **`docker-compose-apps.yml`** — **`SPRING_PROFILES_ACTIVE=docker`** и **`SPRING_CLOUD_*`** (как в оркестраторе).

**Как проверить**

1. Поднять стек (как в **V**, шаги 1–2).

2. В логах при старте: **`The following 1 profile is active: "docker"`** (или аналог Spring Boot).

3. Убедиться, что приложение реально использует значения из окружения compose: в логах провайдера строка подключения к ZK — **`zookeeper:2181`** (имя сервиса из compose), а не **`localhost:2181`**.

4. Health на портах из профиля: **`curl … http://127.0.0.1:8080/actuator/health`** и **`:8082`** — **UP** (тот же код и те же эндпоинты, что локально, другие только адреса/профиль).

### XI. Журналирование

**Реализация:** в каждом модуле **`logback-spring.xml`** — только **`include`** на **`org/springframework/boot/logging/logback/base.xml`** (вывод в консоль, без своих файловых appenders).

**Как проверить**

1. `docker compose -f docker-compose-apps.yml up -d`
2. `docker compose -f docker-compose-apps.yml logs -f currency-rate-provider` — непрерывный поток строк (баннер Spring, Tomcat, ZK, gRPC, **`GrpcServerObservabilityInterceptor`** и т.д.) — это **stdout** контейнера, как заберёт оркестратор / `docker logs`.
3. То же: **`logs -f rate-printer`**.

## Запуск

Нужны **4 терминала** (или 3, если запускаете один провайдер). Порядок важен.

---

### Терминал 1 — ZooKeeper

```bash
# Если контейнер zookeeper уже есть:
docker start zookeeper

# Если запускаете впервые или нужно пересоздать:
# docker rm -f zookeeper
# docker run -d --name zookeeper -p 2181:2181 zookeeper:3.8
```

Проверка: `docker ps` — контейнер zookeeper должен быть в статусе Up.

---

### Терминал 1 (или любой) — сборка проекта

```bash
cd ~/software-design
mvn clean package -DskipTests
```

Выполнить один раз перед первым запуском или после изменений в коде.

---

### Терминал 2 — первый провайдер

```bash
cd ~/software-design/currency-rate-provider
java -jar target/currency-rate-provider-0.0.1-SNAPSHOT.jar
```

Дождаться строки `gRPC Server started, listening on address: *, port: 9090`. Окно не закрывать.

---

### Терминал 3 — второй провайдер (опционально, для балансировки)

```bash
cd ~/software-design/currency-rate-provider
java -jar target/currency-rate-provider-0.0.1-SNAPSHOT.jar --server.port=8081 --grpc.server.port=9091 --spring.cloud.zookeeper.discovery.metadata.gRPC_port=9091 --management.metrics.tags.application=service2
```

Дождаться строки `gRPC Server started, listening on address: *, port: 9091`. Окно не закрывать.

---

### Терминал 4 — Rate Printer (клиент)

```bash
cd ~/software-design/rate-printer
java -jar target/rate-printer-0.0.1-SNAPSHOT.jar
```

Появится вывод курса каждые 5 секунд, например:
```
[2026-02-14T16:30:00] USDRUB: 85.1234
[2026-02-14T16:30:05] USDRUB: 78.5678
```

Клиент находит провайдеров через ZooKeeper и распределяет запросы между ними (round-robin).

---

### Порядок запуска

1. ZooKeeper  
2. Сборка (если нужно)  
3. Первый провайдер  
4. Второй провайдер (по желанию)  
5. Rate Printer  

---

### Конфигурация ZooKeeper

По умолчанию подключение к `localhost:2181`. Для другого адреса — в `application.properties` или через аргументы:

```
spring.cloud.zookeeper.connect-string=host:2181
```

## Мониторинг: Actuator, Micrometer, Prometheus, Grafana

Подключены **Spring Boot Actuator** и экспорт метрик в **Prometheus** (формат, совместимый с дашбордом [JVM (Micrometer) — Grafana ID 4701](https://grafana.com/grafana/dashboards/4701-jvm-micrometer/)). Описание метрик: [Spring Boot Actuator — Metrics](https://docs.spring.io/spring-boot/docs/2.7.18/reference/html/actuator.html#actuator.metrics).

Эндпоинт для Prometheus: `GET /actuator/prometheus` на HTTP-порту приложения.

Тег **`application`** (для переменных дашборда 4701):

| Сервис | Модуль | HTTP-порт | `application` |
|--------|--------|-----------|----------------|
| Клиент | **rate-printer** | **8082** | `client` |
| Первый провайдер | **currency-rate-provider** | 8080 | `service1` |
| Второй провайдер | **currency-rate-provider** | 8081 | `service2` (задаётся аргументом, см. выше) |

**ZooKeeper** — отдельный JVM-процесс: Micrometer-метрик с именами `jvm_*` у него нет. В репозитории добавлены **JMX Exporter** и дашборд **ZooKeeper JVM (JMX)** в Grafana (heap, потоки, uptime, CPU по MBean). Дашборд **4701** используйте для **client / service1 / service2**.

### Стек в Docker

```bash
cd ~/software-design
docker compose -f docker-compose-monitoring.yml up -d
```

- **Grafana:** http://localhost:3000 (логин/пароль по умолчанию: `admin` / `admin`). Дашборды подхватываются из `monitoring/grafana/dashboards/`.
- **Prometheus:** http://localhost:9092
- **JMX ZooKeeper:** порт **9404** (`/metrics` внутри контейнера экспортера)

Compose поднимает **свой** ZooKeeper на **2181**. Остановите другой контейнер с тем же портом или измените проброс портов в `docker-compose-monitoring.yml`. Запускайте **rate-printer** и провайдеры с `spring.cloud.zookeeper.connect-string=localhost:2181` (как в примерах выше).

На **Linux** Prometheus обращается к приложениям на хосте через `host.docker.internal` (в compose уже добавлен `extra_hosts`). Сначала поднимите мониторинг, затем соберите и запустите сервисы на хосте на портах **8080**, **8081** (второй провайдер, опционально) и **8082** (клиент).

### Куда заходить в интерфейсе

**Быстрая проверка с хоста** (метрики с приложений):

```bash
curl -s http://localhost:8080/actuator/prometheus | head
curl -s http://localhost:8082/actuator/prometheus | head
```

**Prometheus** → http://localhost:9092  

- **Status → Targets** — job **`spring-actuator`**: для каждого запущенного сервиса endpoint должен быть **UP** (например `host.docker.internal:8080` … `8082`). Job **`zookeeper`** — **UP**.  
- Вкладка **Graph** — пробный запрос, например `jvm_memory_used_bytes` или `up{job="spring-actuator"}`: должны появляться серии.

**Grafana** → http://localhost:3000 (первый вход: **admin** / **admin**).  

- Слева **Dashboards** (или **☰ → Dashboards**): откройте **JVM (Micrometer)** (дашборд в духе Grafana 4701).  
- **Время** — выпадающий список **в правом верхнем углу** (например *Last 15 minutes* или *Last 1 hour*), при необходимости кнопка обновления рядом.  
- Переменные **над панелями**: **Application** — `client`, `service1` или `service2`; **Instance** — строка с нужным портом (часто `host.docker.internal:8080` / `:8081` / `:8082`). Без подходящей пары переменных графики могут быть пустыми.  
- Отдельно: дашборд **ZooKeeper JVM (JMX)** — метрики процесса ZooKeeper (не Micrometer).  
- При переходе на другой дашборд Grafana может спросить, сохранить ли изменения — для просмотра достаточно **Discard / не сохранять** (сохранять только если сами правили дашборд и хотите это хранить).

Дополнительно в Grafana: **Explore** — тот же Prometheus datasource, можно выполнить тот же PromQL, что и в Prometheus UI.

### Логи и прикладные метрики (gRPC / REST)

**Логи** пишутся в **консоль** процесса (терминал, где запущен JAR): при старте — строка с **версией** (`Application started: name=... version=...` из `build-info`); на **провайдере** — **gRPC** запрос/ответ (`GrpcServerObservabilityInterceptor`) и **REST** `GET /api/rate` (тело запроса/ответа); на **клиенте** — **gRPC** запрос/ответ (`GrpcClientLoggingInterceptor`). Клиент передаёт заголовок **`x-client-id`** (по умолчанию `client`, см. `grpc.observability.client-id` в `rate-printer`), чтобы на сервере различать вызывающих в метриках.

**Прикладные метрики** (Micrometer, только на **провайдере**): префикс **`currency_`** в `/actuator/prometheus`. Примеры проверки:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep '^currency_'
```

- **`currency_grpc_server_requests_seconds_*`** — длительность gRPC-вызовов; RPS: `sum by (client) (rate(currency_grpc_server_requests_seconds_count[1m]))` в Prometheus или Grafana **Explore**.
- **`currency_grpc_server_errors_http500_total`** — ответы gRPC со статусом **INTERNAL** (учёт «как 500»).
- **`currency_http_server_requests_seconds_*`** / **`currency_http_server_errors_http500_total`** — **REST** `/api/**`, идентификация вызывающего — IP (`client`).

У всех этих серий в Prometheus, кроме явно перечисленных ниже полей, есть общий тег **`application`** из `management.metrics.tags.application` (**`service1`** / **`service2`** на соответствующем провайдере).

| Логическое имя (Micrometer) | В Prometheus (основные серии) | Теги (лейблы) |
|-----------------------------|-------------------------------|---------------|
| `currency.grpc.server.requests` (Timer) | `currency_grpc_server_requests_seconds_count`, `_sum`, `_max`; то же имя с `quantile` 0.5, 0.95, 0.99 | **`application`**, **`client`** (`x-client-id`, иначе `unknown`) |
| `currency.grpc.server.errors.http500` (Counter) | `currency_grpc_server_errors_http500_total` | **`application`**, **`client`** |
| `currency.http.server.requests` (Timer) | `currency_http_server_requests_seconds_*` (как у Timer выше) | **`application`**, **`client`** (remote IP или `unknown`) |
| `currency.http.server.errors.http500` (Counter) | `currency_http_server_errors_http500_total` | **`application`**, **`client`** |

Средняя задержка gRPC по клиенту:  
`sum by (client)(rate(currency_grpc_server_requests_seconds_sum[5m])) / sum by (client)(rate(currency_grpc_server_requests_seconds_count[5m]))`.  
Перцентили: серии с `quantile="0.5"`, `0.95`, `0.99` у `currency_grpc_server_requests_seconds`.

## API

**Currency Rate Provider** — gRPC-сервис `CurrencyRateService` с методом `GetUsdRubRate`:
- Вход: пустой запрос
- Выход: `rate` — число (курс USDRUB)

Курс формируется как базовая величина ~80.0 ± случайное значение для каждого запроса (± 10.0).

## Контрактное тестирование (Pact)

Используется Pact для проверки контракта между **rate-printer** (consumer) и **currency-rate-provider** (provider). Контракты хранятся в Pact Broker.

**Нужен Docker Compose V2** (команда `docker compose` с пробелом). Проверка: `docker compose version` — должна быть версия 2.x. Если установлен только старый `docker-compose` (через pip), возможны ошибки; лучше установить [Docker Engine с плагином Compose](https://docs.docker.com/compose/install/).

### 1. Запустить Pact Broker

В корне проекта:

```bash
cd ~/software-design
docker compose -f docker-compose-pact-broker.yml up -d
```

Проверка: в браузере открыть http://localhost:9292 — должна открыться страница Pact Broker.

### 2. Проверить контракты (consumer → broker → provider)

Порядок важен: сначала consumer публикует контракт, потом provider его подтягивает.

**Шаг 1 — consumer (rate-printer):** тесты генерируют контракт и публикуют в broker.

```bash
cd ~/software-design/rate-printer
mvn clean verify
```

**Шаг 2 — provider (currency-rate-provider):** тесты загружают контракты из broker и проверяют REST API `GET /api/rate`.

```bash
cd ~/software-design/currency-rate-provider
mvn clean verify
```

Оба шага должны завершиться без ошибок. Контракты можно посмотреть в UI broker’а: http://localhost:9292

### Сборка и проверка всего проекта из корня

Из корня можно один раз прогнать сборку и все проверки (включая Pact):

```bash
cd ~/software-design
mvn clean verify
```

При этом по очереди собираются и проходят фазу `verify` все модули: **currency-api** → **currency-rate-provider** (тесты + Pact-верификация по контрактам из broker) → **rate-printer** (тесты + публикация контрактов в broker). Выполнение занимает несколько минут.

**Нужно заранее:** Pact Broker запущен (`docker compose -f docker-compose-pact-broker.yml up -d`), и порты 8080/9090 не заняты другими экземплярами приложений (иначе тесты могут падать с «Адрес уже используется»).

### Без broker (только тесты consumer)

Контракты можно сгенерировать локально без публикации:

```bash
cd ~/software-design/rate-printer
mvn test -Dtest=CurrencyRateContractTest
```

Файлы появятся в `rate-printer/target/pacts/`. Полная верификация провайдера по контрактам из broker требует запущенного broker (шаги выше).

