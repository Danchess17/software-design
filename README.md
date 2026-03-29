# Homework 4: Currency Rate Services

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

