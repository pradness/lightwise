# User Service

`user-service` is a Spring Boot application that manages users for the Lightwise system.
It exposes a REST API, stores users in MySQL, and uses Flyway to create and update the
database schema.

This guide explains the project as if you are new to Java and Spring.

## What this service does

The service currently supports:

- Creating a user
- Finding one user by ID
- Updating a user
- Deleting a user
- Returning useful HTTP responses when a user does not exist
- Logging service calls
- Measuring controller execution time
- Creating database tables through versioned Flyway migrations

The project also contains a `device` database table created by a migration. The Java
code does not yet contain a device entity, repository, service, or controller, so device
operations are not available through this service yet.

## Technology used

| Technology | Why it is used |
| --- | --- |
| Java 21 | The programming language and runtime version for the application. |
| Spring Boot | Starts the application and configures common Spring components automatically. |
| Spring Web MVC | Turns Java methods into HTTP REST endpoints and converts JSON request/response bodies. |
| Spring Data JPA | Provides database access through Java interfaces instead of handwritten SQL for normal CRUD operations. |
| JPA / Hibernate | Maps Java objects to database rows. Hibernate is the JPA implementation used at runtime. |
| MySQL Connector/J | JDBC driver that lets Java connect to MySQL. |
| Flyway | Applies database migrations in a known order and remembers which migrations have already run. |
| Flyway MySQL support | Adds MySQL-specific Flyway support. |
| AspectJ / Spring AOP | Runs logging and timing code around selected methods without putting that code in every controller or service method. |
| SLF4J | Logging API used by the application. Spring Boot supplies the actual logging implementation. |
| Lombok | Generates constructors, getters, setters, builders, and logging fields during compilation. |
| Springdoc OpenAPI | Generates OpenAPI documentation and a Swagger UI for the REST API. |
| Spring Boot DevTools | Provides development conveniences such as automatic restart while developing. |
| JUnit 5 | Test framework used for Java tests. |
| Spring Boot test starters | Provide Spring-aware test support for the web, JPA, and Flyway parts of the application. |

## Project layout

```text
user-service/
├── pom.xml
├── mvnw
├── mvnw.cmd
└── src/
    ├── main/
    │   ├── java/com/lightwise/user_service/
    │   │   ├── UserServiceApplication.java
    │   │   ├── aspect/
    │   │   │   ├── ExecutionTimeAspect.java
    │   │   │   └── LoggingAspect.java
    │   │   ├── controller/
    │   │   │   └── UserController.java
    │   │   ├── dto/
    │   │   │   └── UserDto.java
    │   │   ├── entity/
    │   │   │   └── User.java
    │   │   ├── exception/
    │   │   │   ├── RestExceptionHandler.java
    │   │   │   └── UserNotFoundException.java
    │   │   ├── repository/
    │   │   │   └── UserRepository.java
    │   │   └── service/
    │   │       └── UserService.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           ├── V1__user_tables.sql
    │           └── V2__device_table.sql
    └── test/
        └── java/com/lightwise/user_service/
            └── UserServiceApplicationTests.java
```

## How a request works

For example, when a client sends `POST /api/v1/users`:

1. Spring receives the HTTP request.
2. `UserController` matches the URL and HTTP method.
3. Spring converts the JSON body into a `UserDto` object.
4. `UserController` calls `UserService`.
5. `UserService` converts the DTO into a `User` entity.
6. `UserRepository` saves the entity through JPA/Hibernate.
7. MySQL stores the row in the `user` table.
8. The saved entity is converted back into a DTO.
9. Spring converts the DTO to JSON and returns it to the client.
10. `ExecutionTimeAspect` logs how long the controller method took, and
    `LoggingAspect` logs service method input and output.

This separation is useful because each layer has one main responsibility:

- **Controller:** HTTP and JSON.
- **Service:** application rules and workflow.
- **Repository:** database access.
- **Entity:** database representation.
- **DTO:** API input and output representation.

## Main application module

### `UserServiceApplication.java`

This is the application entry point. Its `main` method calls
`SpringApplication.run(...)`, which starts the embedded web server and creates the
Spring application context.

`@SpringBootApplication` is a combination of three important Spring features:

- Configuration for the application
- Component scanning, which finds classes such as `@Service` and `@Controller`
- Spring Boot auto-configuration, which configures common infrastructure based on the
  dependencies in `pom.xml`

## Controller module

### `controller/UserController.java`

`UserController` is the HTTP/API layer. `@RestController` tells Spring that its methods
handle web requests and that returned objects should normally be written as JSON.

`@RequestMapping("/api/v1/users")` defines the common URL prefix. The `v1` makes it
possible to introduce a future API version without unexpectedly changing existing
clients.

Available endpoints:

| Method | URL | Purpose | Success response |
| --- | --- | --- | --- |
| `POST` | `/api/v1/users` | Create a user | `201 Created` with the created user |
| `GET` | `/api/v1/users/{id}` | Find a user | `200 OK`, or `404 Not Found` |
| `PUT` | `/api/v1/users/{id}` | Replace user fields | `200 OK` with a text message |
| `DELETE` | `/api/v1/users/{id}` | Delete a user | `204 No Content` |

`@PathVariable` reads the `{id}` portion of the URL. `@RequestBody` reads JSON from the
request body. `ResponseEntity` lets the controller choose both the HTTP status and
response body.

Example create request:

```http
POST http://localhost:8080/api/v1/users
Content-Type: application/json
```

```json
{
  "name": "Ada",
  "surname": "Lovelace",
  "email": "ada@example.com",
  "address": "London",
  "alerting": true,
  "energyAlertingThreshold": 25.5
}
```

Example response:

```json
{
  "id": 1,
  "name": "Ada",
  "surname": "Lovelace",
  "email": "ada@example.com",
  "address": "London",
  "alerting": true,
  "energyAlertingThreshold": 25.5
}
```

## DTO module

### `dto/UserDto.java`

DTO means **Data Transfer Object**. `UserDto` is the shape of user data exchanged
through the REST API.

It contains:

- `id`
- `name`
- `surname`
- `email`
- `address`
- `alerting`
- `energyAlertingThreshold`

The service does not expose the JPA entity directly from its API. Using a DTO gives the
API its own data format and prevents database implementation details from becoming part
of the public contract.

The Lombok annotations have these effects:

- `@Data`: getters, setters, `equals`, `hashCode`, and `toString`
- `@Builder`: fluent builder syntax such as `UserDto.builder().name("Ada").build()`
- `@NoArgsConstructor`: constructor with no arguments
- `@AllArgsConstructor`: constructor with all fields

## Entity module

### `entity/User.java`

`User` is a JPA entity. An entity is a Java object that Hibernate can persist as a row
in a database table.

- `@Entity` marks the class as managed by JPA.
- `@Table(name = "user")` maps it to the MySQL table named `user`.
- `@Id` marks `id` as the primary key.
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` tells MySQL to generate the ID
  using its auto-increment feature.

The fields correspond to columns in the `user` table. The service converts between
`UserDto` and `User` in `UserService`.

The Lombok annotations provide constructors, a builder, getters, setters, and common
object methods. `@NoArgsConstructor` is particularly useful because JPA requires a
no-argument constructor.

## Service module

### `service/UserService.java`

`UserService` contains the application logic between the controller and repository.
`@Service` registers it as a Spring-managed service.

Its methods work as follows:

- `createUser`: builds a `User` entity from the input DTO, saves it, and returns the
  saved data as a DTO.
- `getUserById`: asks the repository to find an ID and converts the result to a DTO.
  It returns `null` when there is no matching user; the controller then returns `404`.
- `updateUser`: finds the existing entity, changes all user fields, and saves it.
  If no entity exists, it throws `UserNotFoundException`.
- `deleteUser`: finds the entity and deletes it. It also throws
  `UserNotFoundException` when necessary.
- `toDto`: a private mapping method that converts an entity into an API DTO.

`@Transactional` groups database work into a transaction. A transaction is a unit of
work that should complete successfully as a whole. It is used on create, update, and
delete operations.

`@Slf4j` is a Lombok annotation that creates an SLF4J logger named `log`. The service
currently has the logger available, while the aspects are the main places that write
service/controller logs.

## Repository module

### `repository/UserRepository.java`

`UserRepository` is an interface extending `JpaRepository<User, Long>`.

This tells Spring Data JPA:

- The repository manages `User` entities.
- The entity's ID type is `Long`.

Because it extends `JpaRepository`, it already provides methods such as:

- `save(user)`
- `findById(id)`
- `findAll()`
- `delete(user)`
- `existsById(id)`

Spring creates the implementation automatically at startup. No SQL is needed for these
basic operations.

## Exception module

### `exception/UserNotFoundException.java`

This is a custom unchecked exception extending `RuntimeException`. It represents the
specific application error “the requested user does not exist”.

### `exception/RestExceptionHandler.java`

`@ControllerAdvice` makes this class a central error handler for controllers.
`@ExceptionHandler(UserNotFoundException.class)` catches that exception and returns:

- HTTP status `404 Not Found`
- The exception message as the response body

This avoids duplicating the same exception-to-HTTP conversion in every controller.

The update and delete controller methods currently also contain local `try/catch`
handling. The central handler is the general mechanism used for the custom exception;
the local handling means those methods may return different responses than the global
handler. This is an area that can be simplified in a future improvement.

## Aspect modules

An aspect contains behavior that applies across many classes, such as logging or
performance measurement. This is called **cross-cutting behavior**. Without aspects,
the same logging and timing code would have to be copied into every method.

### `aspect/LoggingAspect.java`

`@Aspect` marks the class as an AspectJ/Spring AOP aspect, and `@Component` allows
Spring to discover it.

The pointcut:

```java
execution(* com.lightwise.user_service.service.*.*(..))
```

matches every method in every class directly inside the service package.

- `@Before` logs the method signature and arguments before a service method runs.
- `@AfterReturning` logs the returned value after successful completion.
- `JoinPoint` provides information about the intercepted method.

`@Slf4j` supplies the SLF4J `log` object. The log messages help diagnose requests and
understand what the service layer is doing.

### `aspect/ExecutionTimeAspect.java`

This aspect measures controller performance. Its pointcut matches methods in the
controller package.

`@Around` means the aspect runs before and after the target method. It records a start
time, calls `joinPoint.proceed()` to execute the real controller method, and finally
logs the elapsed time in milliseconds. The `finally` block ensures the timing log is
written even if the controller throws an exception.

`TimeUnit.NANOSECONDS.toMillis(...)` converts the precise nanosecond measurement into
milliseconds, which are easier to read in logs.

## Configuration

### `src/main/resources/application.properties`

The current configuration includes:

```properties
spring.application.name=user-service
service.port=8080
spring.datasource.url=jdbc:mysql://localhost:3306/lightwise
spring.datasource.username=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.ddl-auto=none
```

The datasource password is configured in the properties file but is intentionally not
documented here. Do not commit real passwords to source control. Prefer an environment
specific configuration or an environment variable/secret in deployment.

The standard Spring Boot property for changing the HTTP port is
`server.port`. The project currently has `service.port=8080`, but that custom property
does not change the embedded server port. Without another configuration, Spring Boot's
default HTTP port is already `8080`.

`spring.jpa.hibernate.ddl-auto=none` is important: Hibernate will not create or alter
tables. Flyway owns schema changes instead.

## Database migrations with Flyway

Migrations are SQL files in:

```text
src/main/resources/db/migration/
```

Flyway uses the naming convention:

```text
V<version>__<description>.sql
```

The two underscores are required. Migrations run in version order and Flyway records
their history in its own metadata table. A migration that has already run is not
normally run again.

### `V1__user_tables.sql`

This migration creates the `user` table with:

- Auto-incrementing `BIGINT` primary key
- Required name, surname, and email
- Optional text address
- `alerting` flag defaulting to false (`0`)
- Energy alert threshold defaulting to `0`
- Unique email constraint

The unique email constraint prevents two users from having the same email address.

### `V2__device_table.sql`

This migration creates the `device` table with:

- Device ID
- Device name, type, and location
- `user_id` referring to the owning user
- An index on `user_id`
- A foreign key to `user.id`
- `ON DELETE CASCADE`, which removes a user's devices when that user is deleted

The table is database infrastructure for a future device feature; there is currently
no Java code using it.

### Preparing MySQL

Create the database before starting the service:

```sql
CREATE DATABASE lightwise
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

Then configure a MySQL user that can connect to `lightwise`, and set the datasource
username and password in the application's configuration. When the application
starts, Flyway connects to MySQL and applies `V1` and `V2` if they have not already
been applied.

## Running the application

Requirements:

- Java 21
- MySQL
- A MySQL database named `lightwise`

From the `user-service` directory:

On Linux/macOS:

```bash
./mvnw spring-boot:run
```

On Windows:

```bat
mvnw.cmd spring-boot:run
```

The API is then available at:

```text
http://localhost:8080/api/v1/users
```

The OpenAPI/Swagger UI supplied by Springdoc is normally available at:

```text
http://localhost:8080/swagger-ui/index.html
```

## Building the application

```bash
./mvnw clean package
```

This compiles the source code, runs enabled tests, and creates a packaged application
in the `target` directory.

To run the packaged application:

```bash
java -jar target/user-service-0.0.1-SNAPSHOT.jar
```

## Tests

Tests are under `src/test/java`.

### `UserServiceApplicationTests.java`

`@SpringBootTest` starts the Spring application context for the test. The
`contextLoads` test verifies that the application can start and that its beans,
database configuration, JPA setup, and migrations can be initialized.

The test also contains `createUsers`, which creates ten sample users through the
repository. It is marked with `@Disabled`, so it does not run during normal test
execution. This prevents an ordinary test run from inserting sample data into the
configured database.

Run the enabled tests with:

```bash
./mvnw test
```

Because the context test uses the configured MySQL datasource, MySQL must be available
and the credentials must be correct when running it. The test dependencies in
`pom.xml` provide Spring Boot support for testing JPA, Flyway, and MVC behavior.

## Maven build file

### `pom.xml`

Maven is the build and dependency management tool. The `pom.xml` declares:

- Project identity and version
- Java 21
- Runtime dependencies
- Test dependencies
- The Spring Boot Maven plugin
- Lombok annotation processing for main and test compilation

Dependencies are downloaded by Maven and placed on the application's classpath.
`runtime` dependencies are needed when the application runs, while `test` dependencies
are used only by tests. Lombok is marked optional because it mainly assists compilation
and is not required as a runtime library.

## Important current behavior

- There is no validation annotation such as `@NotBlank` or `@Email` on `UserDto`, so
  invalid or incomplete request data is not rejected by Bean Validation yet.
- There is no authentication or authorization in this service.
- The API uses a `double` for the energy threshold. For values requiring exact decimal
  precision, a decimal type such as `BigDecimal` may be more appropriate.
- `GET` returns `404` directly when no user exists; update and delete use custom
  exception handling.
- Device schema exists, but device API functionality has not been implemented.
- Database schema changes should be added as a new migration such as `V3__...sql`;
  existing migrations should not be edited after they have been applied.

