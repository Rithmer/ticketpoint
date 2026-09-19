# TicketPoint

TicketPoint — консольное приложение для управления билетными бронированиями. Это развиваемый pet-проект для практики Java, PostgreSQL, SQL, тестирования и архитектуры прикладных систем.

## Возможности

- управление клиентами и мероприятиями;
- создание, поиск, фильтрация, сортировка и удаление бронирований;
- статусы `NEW`, `PAID`, `CANCELLED` с проверкой переходов;
- фиксация цены билета на момент бронирования;
- статистика и экспорт в Excel `.xlsx`;
- интерактивное меню, `--check` и командный экспорт.

## Технологии

Java 17, PostgreSQL, JDBC, Maven Wrapper, Apache POI, JUnit 5 и Docker Compose.

## Архитектура

```text
Console UI → Services → Repository interfaces → JDBC repositories → PostgreSQL
```

`ui` отвечает за консоль, `service` — за бизнес-правила, `repository` — за SQL/JDBC, `model` — за сущности и валидацию, `export` — за выгрузку, `config` — за подключение и логирование.

## Требования

- JDK 17 или новее;
- Docker Compose либо доступный PostgreSQL;
- интернет при первой сборке зависимостей.

Maven устанавливать отдельно не нужно: в репозитории есть Maven Wrapper.

## Быстрый запуск

```bash
cp .env.example .env
docker compose up -d --wait
./mvnw clean package -DskipTests
set -a
source .env
set +a
java -jar target/ticketpoint-1.0-SNAPSHOT.jar
```

На Windows переменные окружения задаются в PowerShell или конфигурации запуска IDE. При первом запуске PostgreSQL применяет `db/schema.sql` и `db/seed.sql`.

## Командная строка

```bash
java -jar target/ticketpoint-1.0-SNAPSHOT.jar --help
java -jar target/ticketpoint-1.0-SNAPSHOT.jar --check
java -jar target/ticketpoint-1.0-SNAPSHOT.jar --export exports/bookings.xlsx
```

`--check` проверяет переменные окружения, подключение и обязательные таблицы. Коды завершения: `0` — успех, `1` — ошибка конфигурации/БД, `2` — некорректные аргументы.

## Конфигурация

| Переменная                     | Значение по умолчанию                          | Назначение                            |
|--------------------------------|------------------------------------------------|---------------------------------------|
| `DB_URL`                       | `jdbc:postgresql://localhost:5432/ticketpoint` | JDBC URL PostgreSQL                   |
| `DB_USER`                      | `ticketpoint_admin`                            | Пользователь БД                       |
| `DB_PASSWORD`                  | отсутствует                                    | Пароль пользователя БД                |
| `APP_ZONE`                     | `Europe/Moscow`                                | Часовой пояс приложения и JDBC-сессий |
| `DB_CONNECT_TIMEOUT_SECONDS`   | `5`                                            | Тайм-аут подключения                  |
| `DB_STATEMENT_TIMEOUT_SECONDS` | `15`                                           | Тайм-аут SQL-операции                 |
| `DB_SOCKET_TIMEOUT_SECONDS`    | `30`                                           | Тайм-аут сетевого чтения              |

Секреты хранятся только в локальном `.env` и исключены из Git. `.env.example` содержит только шаблон.

## Тестирование

```bash
./mvnw test
./mvnw clean verify
```

Для PostgreSQL-интеграционных тестов задайте `TEST_DB_URL`, `TEST_DB_USER` и `TEST_DB_PASSWORD`. Сборка компилируется с `-Xlint:all -Werror`.

## Docker production-профиль

```bash
./mvnw package -DskipTests
docker compose --profile production run --rm ticketpoint
```
