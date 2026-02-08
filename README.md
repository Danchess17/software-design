# Homework 1: Currency Rate Services

Два сервиса на Java с использованием Spring Boot и gRPC.

## Структура проекта

- **currency-api** — общий модуль с gRPC-определениями (proto)
- **currency-rate-provider** — сервис 1: сервер, возвращающий курс USDRUB
- **rate-printer** — сервис 2: клиент, выводящий курс каждые 5 секунд

## Требования

- Java 11+
- Maven 3.6+

## Запуск

### 1. Сборка

```bash
mvn clean package -DskipTests
```

### 2. Запуск Currency Rate Provider (сервер)

```bash
cd currency-rate-provider && java -jar target/currency-rate-provider-0.0.1-SNAPSHOT.jar
```

Сервер поднимается на порту **9090** (gRPC).

### 3. Запуск Rate Printer (клиент)

В отдельном терминале (сначала запустите сервер):

```bash
cd rate-printer && java -jar target/rate-printer-0.0.1-SNAPSHOT.jar
```

Клиент каждые 5 секунд запрашивает курс и выводит его на экран, например:
```
[2026-02-07T17:39:26] USDRUB: 79.6501
[2026-02-07T17:39:31] USDRUB: 82.3346
```

## API

**Currency Rate Provider** — gRPC-сервис `CurrencyRateService` с методом `GetUsdRubRate`:
- Вход: пустой запрос
- Выход: `rate` — число (курс USDRUB)

Курс формируется как базовая величина ~80.0 ± случайное значение для каждого запроса (± 10.0).
