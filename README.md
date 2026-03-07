# Homework 3: Currency Rate Services

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
java -jar target/currency-rate-provider-0.0.1-SNAPSHOT.jar --server.port=8081 --grpc.server.port=9091 --spring.cloud.zookeeper.discovery.metadata.gRPC_port=9091
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

