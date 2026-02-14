# Homework 2: Currency Rate Services

Два сервиса на Java с использованием Spring Boot и gRPC.

## Структура проекта

- **currency-api** — общий модуль с gRPC-определениями (proto)
- **currency-rate-provider** — сервис 1: сервер, возвращающий курс USDRUB
- **rate-printer** — сервис 2: клиент, выводящий курс каждые 5 секунд

## Требования

- Java 11+
- Maven 3.6+
- Apache ZooKeeper (для service discovery)

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
