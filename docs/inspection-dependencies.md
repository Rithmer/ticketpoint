# Обновление зависимостей по результатам Inspect Code

Проверено 23 октября 2026 года. Исходный отчёт IntelliJ IDEA `VulnerableLibrariesLocal` содержал 30 различных уязвимых координат Maven, повторявшихся у нескольких прямых зависимостей. Исправление выполнено обновлением реально разрешаемых зависимостей, без отключения инспекции и без suppression.

## Почему потребовался Spring Boot 4

Обновления только до Spring Boot 3.5.16 недостаточно. Это последний открытый релиз ветки 3.5, поставляющий Spring Framework 6.2.19. Официальное уведомление CVE-2026-59283 включает 6.2.0–6.2.19 в уязвимый диапазон и предлагает открытое исправление 7.0.9; исправление 6.2.20 доступно только по коммерческой поддержке. Аналогично CVE-2026-47842 и CVE-2026-59276 исправлены в открытой ветке Spring Security начиная с 7.0.7, тогда как 6.5.12 — коммерческий релиз. Поэтому для устранения предупреждений без платного репозитория выбрана поддерживаемая ветка Boot 4.0 с Framework 7 и Security 7. [Объявление Boot 3.5.16](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/), [зависимости Boot 3.5](https://docs.spring.io/spring-boot/3.5/appendix/dependency-versions/coordinates.html), [CVE-2026-59283](https://spring.io/security/cve-2026-59283/), [CVE-2026-47842](https://spring.io/security/cve-2026-47842/), [CVE-2026-59276](https://spring.io/security/cve-2026-59276/).

Версии всей платформы определяет единый `spring-boot-dependencies:4.0.9` в родительском POM. Это исключает прежнюю ситуацию, когда backend использовал одну версию PostgreSQL из BOM, а core и JavaFX — другую явно заданную версию. Подключение BOM через `dependencyManagement` и явные переопределения перед импортом BOM соответствуют документации Spring Boot. [Управление зависимостями без Spring Boot parent](https://docs.spring.io/spring-boot/4.0/maven-plugin/using.html).

## Разрешённые версии

Таблица получена из `./mvnw -B dependency:tree`; значения платформы без точечных переопределений также опубликованы в [официальной таблице Boot 4.0.9](https://docs.spring.io/spring-boot/4.0/appendix/dependency-versions/coordinates.html).

| Группа                           | Было в отчёте    | Стало                                   |
| -------------------------------- | ---------------- | --------------------------------------- |
| Spring Boot                      | 3.5.5            | 4.0.9                                   |
| Spring Framework                 | 6.2.10           | 7.0.10                                  |
| Spring Security                  | 6.5.3            | 7.0.8                                   |
| Spring Data Commons / JPA        | 3.5.3            | 4.0.8                                   |
| Logback Classic / Core           | 1.5.18           | 1.6.5                                   |
| Log4j API                        | 2.24.3           | 2.26.1                                  |
| Jackson 2 Core / Databind / YAML | 2.19.2           | 2.21.8                                  |
| Jackson 2 TOML                   | 2.19.2           | отсутствует в дереве                    |
| Jackson 3 Core / Databind        | отсутствовал     | 3.1.8                                   |
| Tomcat Embed                     | 10.1.44          | 11.0.27                                 |
| PostgreSQL JDBC                  | 42.7.7 / 42.7.13 | 42.7.14                                 |
| Micrometer Core                  | 1.15.3           | 1.16.8                                  |
| JSON Smart                       | 2.5.2            | исключён; JsonPath использует Jackson   |
| Commons Lang                     | 3.17.0           | 3.19.0                                  |
| AssertJ Core                     | 3.27.4           | 3.27.7                                  |
| ASM                              | 9.7.1            | отсутствует после исключения json-smart |

Точечные дополнения к BOM:

- PostgreSQL 42.7.14 исправляет оба отмеченных в исходном отчёте CVE-2026-107314 и CVE-2026-107315; именно эту версию уже выбирает Boot 4.0.9. Рекомендация производителя — обновить драйвер. [Официальные security advisories pgJDBC](https://jdbc.postgresql.org/security/).
- Log4j BOM 2.26.1 выравнивает API, мосты и остальные артефакты во всех модулях, включая транзитивную зависимость Apache POI. Apache рекомендует 2.25.5 либо 2.26.1 для CVE-2026-49844. Выбран 2.26.1, соответствующий уже использовавшемуся JavaFX-мосту `log4j-to-jul`. [Apache Logging security](https://logging.apache.org/security.html).
- ASM удалён из разрешённого дерева вместе с `accessors-smart`, поскольку оба артефакта поступали через исключённый json-smart. Явное переопределение ASM больше не требуется.
- Logback Classic и Core обновлены совместно до 1.6.5. Повторная инспекция выявила, что 1.5.38 из BOM сохраняет CVE-2026-19880. Производитель отмечает первое исправление в 1.6.3 и дополнительное исправление обхода CVE-2026-104721 в 1.6.5. Поэтому выбран последний исправленный релиз, а не только минимальная версия, закрывающая исходное предупреждение. [Logback release 1.6.5](https://logback.qos.ch/news.html#1.6.5).
- Jackson BOM обеих совместимых веток переопределён до 2.21.8 и 3.1.8: advisory производителя от 20 октября 2026 года указывает эти версии как исправляющие CVE-2026-110879. Это сохраняет согласованность версий всех модулей Jackson внутри каждой ветки. [FasterXML security advisory](https://github.com/FasterXML/jackson-databind/security/advisories/GHSA-m8xf-2cr7-77ph).
- Для json-smart CVE-2026-113602 нет опубликованного исправления; последний релиз Maven Central — 2.6.0, он также уязвим. Библиотека исключена из обеих тестовых starter-зависимостей. JsonPath остаётся и использует документированный JacksonJsonProvider/JacksonMappingProvider, поэтому JSON-утверждения интеграционных тестов сохраняются. [Issue производителя json-smart](https://github.com/netplex/json-smart-v2/issues/302), [JsonPath JsonProvider SPI](https://github.com/json-path/JsonPath#jsonprovider-spi).

## Совместимость приложения

Миграция следует официальному руководству Boot 4: используются модульные starter-зависимости Web MVC, Flyway, Web MVC Test и Security Test. Jackson 3 использует пакет `tools.jackson`, при этом аннотации остаются в `com.fasterxml.jackson.annotation`. Тестовая аннотация `AutoConfigureMockMvc` и автоконфигурация `UserDetailsServiceAutoConfiguration` перенесены в новые пакеты. Старый Jackson 2 остаётся как совместимая транзитивная зависимость JJWT и Swagger, но его версии управляются безопасным BOM. [Boot 4 migration guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide).

Для Spring Boot 4.0 выбрана springdoc 3.0.3: таблица совместимости производителя сопоставляет Boot 4.0.x с springdoc 3.0.x. [springdoc compatibility matrix](https://springdoc.org/#what-is-the-compatibility-matrix-of-springdoc-openapi-with-spring-boot).

## Проверка

`./mvnw -B dependency:tree` завершился `BUILD SUCCESS` для всех четырёх модулей. Ни одна исходная уязвимая координата с прежней версией не осталась в разрешённом дереве. Это проверка разрешения зависимостей; окончательный результат проверки уязвимостей устанавливается отдельным повторным запуском IntelliJ Inspect Code, поскольку база сканера обновляется независимо от Maven.
