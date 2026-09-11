# Device Service

`device-service` is a Spring Boot application that manages devices belonging to
Lightwise users. It exposes a REST API for creating, reading, updating, and deleting
devices, and stores device data in MySQL.

This guide explains the project from the beginning, assuming no previous Java or Spring
experience.

## What this service does

The service currently supports:

- Creating a device
- Finding one device by ID
- Updating a device
- Deleting a device
- Restricting device types to a predefined Java enum
- Returning a `404 Not Found` response when a device does not exist
- Logging service calls
- Measuring controller execution time
- Defining the device database table with an SQL migration

A device contains a name, type, location, and the ID of the user who owns it. The
service stores `userId` as a number; it does not load a complete `User` object from the
user service.

## Technology used

| Technology | Why it is used |
| --- | --- |
| Java 21 | The programming language and runtime version used by the project. |
| Spring Boot | Starts the application and automatically configures common Spring components. |
| Spring Web MVC | Maps HTTP requests to Java methods and converts JSON request/response bodies. |
| Spring Data JPA | Provides repository methods for database CRUD operations. |
| JPA / Hibernate | Maps the `Device` Java object to the `device` MySQL table. Hibernate is the JPA implementation used at runtime. |
| MySQL Connector/J | JDBC driver that allows Java to connect to MySQL. |
| AspectJ / Spring AOP | Runs logging and execution-time code around selected methods without duplicating that code everywhere. |
| SLF4J | Logging API used by the aspects. Spring Boot supplies the logging implementation. |
| Lombok | Generates constructors, getters, setters, builders, and common object methods during compilation. |
| Springdoc OpenAPI | Generates OpenAPI documentation and a Swagger UI for the HTTP API. |
| JUnit 5 | Test framework used by the test class. |
| Spring Boot test starters | Provide Spring-aware testing support for JPA and MVC. |
| Maven | Downloads dependencies, compiles the project, runs tests, and packages the application. |

## Project layout

```text
device-service/
├── pom.xml
├── mvnw
├── mvnw.cmd
└── src/
    ├── main/
    │   ├── java/com/lightwise/device_service/
    │   │   ├── DeviceServiceApplication.java
    │   │   ├── aspect/
    │   │   │   ├── ExecutionTimeAspect.java
    │   │   │   └── LoggingAspect.java
    │   │   ├── controller/
    │   │   │   └── DeviceController.java
    │   │   ├── dto/
    │   │   │   └── DeviceDto.java
    │   │   ├── entity/
    │   │   │   └── Device.java
    │   │   ├── exception/
    │   │   │   ├── DeviceNotFoundException.java
    │   │   │   └── RestExceptionHandler.java
    │   │   ├── model/
    │   │   │   └── DeviceType.java
    │   │   ├── repository/
    │   │   │   └── DeviceRepository.java
    │   │   └── service/
    │   │       └── DeviceService.java
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/
    │           └── V1__device_table.sql
    └── test/
        └── java/com/lightwise/device_service/
            └── DeviceServiceApplicationTests.java
```

## How a request works

For example, when a client sends `POST /api/v1/devices/create`:

1. Spring receives the HTTP request.
2. `DeviceController` matches the URL and HTTP method.
3. Spring converts the JSON body into a `DeviceDto`.
4. The controller calls `DeviceService`.
5. The service copies the DTO values into a `Device` entity.
6. `DeviceRepository` saves the entity through JPA and Hibernate.
7. MySQL stores the row in the `device` table.
8. The saved entity is converted back into a DTO.
9. Spring converts the DTO into JSON and sends the response.
10. The aspects log the service call and controller execution time.

The layers have separate responsibilities:

- **Controller:** HTTP URLs, request bodies, and response statuses.
- **Service:** application operations and not-found checks.
- **Repository:** database access.
- **Entity:** database representation.
- **DTO:** data sent into and returned from the API.
- **Model:** allowed device types.

## Main application module

### `DeviceServiceApplication.java`

This is the entry point of the application. The `main` method calls
`SpringApplication.run(...)`, which starts Spring Boot and its embedded web server.

`@SpringBootApplication` enables:

- Application configuration
- Component scanning, which finds classes annotated with `@Service`, `@Repository`,
  `@RestController`, and similar annotations
- Spring Boot auto-configuration based on the dependencies in `pom.xml`

## Controller module

### `controller/DeviceController.java`

`DeviceController` is the REST API layer. `@RestController` tells Spring that this
class handles HTTP requests and that returned objects should be serialized as JSON.

`@RequestMapping("api/v1/devices")` defines the common URL prefix for all methods in
the class. The API version is included so a future version can be introduced without
breaking existing clients.

Available endpoints:

| Method | URL | Purpose | Success response |
| --- | --- | --- | --- |
| `GET` | `/api/v1/devices/{id}` | Find a device by ID | `200 OK` with the device |
| `POST` | `/api/v1/devices/create` | Create a device | `200 OK` with the created device |
| `PUT` | `/api/v1/devices/{id}` | Update a device | `200 OK` with the updated device |
| `DELETE` | `/api/v1/devices/{id}` | Delete a device | `204 No Content` |

`@PathVariable` reads the `{id}` value from the URL. `@RequestBody` reads JSON from
the request and converts it to a `DeviceDto`. `ResponseEntity` allows the method to
choose the HTTP status and response body.

Example create request:

```http
POST http://localhost:8081/api/v1/devices/create
Content-Type: application/json
```

```json
{
  "name": "Living room light",
  "type": "LIGHT",
  "location": "Living room",
  "userId": 1
}
```

Example response:

```json
{
  "id": 1,
  "name": "Living room light",
  "type": "LIGHT",
  "location": "Living room",
  "userId": 1
}
```

The controller uses port `8081`, configured in `application.properties`.

## DTO module

### `dto/DeviceDto.java`

DTO means **Data Transfer Object**. `DeviceDto` describes the data exchanged through
the REST API:

- `id`: database identifier
- `name`: human-readable device name
- `type`: one of the values in `DeviceType`
- `location`: where the device is installed
- `userId`: ID of the owning user

The API uses a DTO instead of exposing the JPA entity directly. This gives the API its
own data format and keeps database implementation details separate from the public
HTTP contract.

Lombok annotations provide the boilerplate code:

- `@Data`: getters, setters, `equals`, `hashCode`, and `toString`
- `@Builder`: builder syntax for creating objects
- `@NoArgsConstructor`: no-argument constructor
- `@AllArgsConstructor`: constructor containing every field

## Entity module

### `entity/Device.java`

`Device` is a JPA entity. JPA entities are Java objects that Hibernate can save as
database rows.

- `@Entity` marks the class as managed by JPA.
- `@Table(name = "device")` maps it to the `device` table.
- `@Id` marks `id` as the primary key.
- `@GeneratedValue(strategy = GenerationType.IDENTITY)` uses MySQL auto-increment
  IDs.
- `@Enumerated(EnumType.STRING)` stores the enum text, such as `LIGHT`, instead of
  an enum number. Storing text is easier to read in the database and safer if enum
  declaration order changes.
- `@Column(name = "type")` and `@Column(name = "user_id")` explicitly connect Java
  fields to database columns.

The `userId` field is a `Long`, not a JPA relationship to a `User` entity. This keeps
the device service independent of the user service's Java classes. The database
migration still defines a foreign key from `device.user_id` to `user.id`.

## Device type module

### `model/DeviceType.java`

`DeviceType` is a Java `enum`. An enum is a fixed list of allowed values:

```text
LIGHT
FAN
FRIDGE
WASHING_MACHINE
TV
OVEN
```

Using an enum prevents arbitrary device type strings from being used by Java code.
JSON requests must use one of these exact values, for example `"type": "FAN"`.

## Service module

### `service/DeviceService.java`

`DeviceService` contains the main device operations. `@Service` registers it as a
Spring-managed object.

Its methods work as follows:

- `getDeviceById`: asks the repository for an ID. If the ID does not exist, it throws
  `DeviceNotFoundException`; otherwise it maps the entity to a DTO.
- `createDevice`: copies the incoming DTO values into a new entity, saves it, and
  returns the saved entity as a DTO.
- `updateDevice`: finds an existing device, replaces its name, type, location, and
  user ID, saves it, and returns the updated DTO.
- `deleteDevice`: checks whether the ID exists and deletes it. If it does not exist,
  it throws `DeviceNotFoundException`.
- `mapToDto`: converts an entity into the DTO returned by the API.

The service does not currently use `@Transactional`. Spring Data repository operations
still perform their own database operations, but a future multi-step operation may
need an explicit transaction around the complete workflow.

## Repository module

### `repository/DeviceRepository.java`

`DeviceRepository` is an interface extending `JpaRepository<Device, Long>`.

This tells Spring Data JPA that:

- The repository manages `Device` entities.
- The entity's primary key type is `Long`.

Spring creates the implementation automatically. The inherited methods include:

- `save(device)`
- `findById(id)`
- `findAll()`
- `existsById(id)`
- `deleteById(id)`
- `delete(device)`

This removes the need to write SQL for the basic CRUD operations.

## Exception module

### `exception/DeviceNotFoundException.java`

This custom unchecked exception represents the error that a requested device does not
exist. It extends `RuntimeException`, so the service can throw it without adding
`throws` declarations to every method.

### `exception/RestExceptionHandler.java`

`@ControllerAdvice` makes this a central exception handler for controllers.
`@ExceptionHandler(DeviceNotFoundException.class)` catches the custom exception and
returns:

- HTTP status `404 Not Found`
- The exception message as the response body

For example, requesting a missing device may return:

```text
Device not found with id 99
```

## Aspect modules

An aspect contains cross-cutting behavior: behavior needed by many methods, such as
logging or performance measurement. AspectJ/Spring AOP allows this behavior to be
defined once instead of copied into every method.

### `aspect/LoggingAspect.java`

The pointcut:

```java
execution(* com.lightwise.device_service.service.*.*(..))
```

matches every method in classes directly inside the service package.

- `@Aspect` marks the class as an aspect.
- `@Component` allows Spring to discover and create it.
- `@Before` logs the method signature and arguments before a service method runs.
- `@AfterReturning` logs the returned value after successful completion.
- `JoinPoint` provides information about the intercepted method.
- `@Slf4j` generates an SLF4J logger named `log`.

The logs help developers understand which service operations are being called and what
they return.

### `aspect/ExecutionTimeAspect.java`

This aspect measures controller performance. Its pointcut matches methods in the
controller package.

`@Around` runs code before and after the real controller method. The aspect:

1. Records the start time with `System.nanoTime()`.
2. Calls `joinPoint.proceed()` so the real controller method executes.
3. Uses a `finally` block so timing is logged even when the method fails.
4. Converts nanoseconds to milliseconds and writes the duration with SLF4J.

`ProceedingJoinPoint` represents the intercepted method and allows the aspect to
continue execution.

## Configuration

### `src/main/resources/application.properties`

The important current settings are:

```properties
spring.application.name=device-service
server.port=8081
spring.datasource.url=jdbc:mysql://localhost:3306/lightwise
spring.datasource.username=root
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.jpa.database-platform=org.hibernate.dialect.MySQLDialect
spring.jpa.hibernate.naming.physical-strategy=org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
spring.jpa.hibernate.ddl-auto=none
```

The datasource password is present in the local properties file but is intentionally
not documented here. Never commit a real password to source control. Use an environment
specific configuration or a secret manager for real deployments.

The service runs on port `8081`. The database URL points to a MySQL database named
`lightwise`.

`spring.jpa.hibernate.ddl-auto=none` means Hibernate will not create or change the
database tables. Schema changes must be performed separately using SQL migrations or
another database migration process.

The naming strategy is explicitly configured so Hibernate uses the names written in
the entity annotations and database schema.

## Database migration

### `src/main/resources/db/migration/V1__device_table.sql`

The migration creates the `device` table with:

- An auto-incrementing `BIGINT` primary key
- Device name
- Device type
- Device location
- `user_id` identifying the owning user
- An index on `user_id`
- A foreign key from `device.user_id` to `user.id`
- `ON DELETE CASCADE`

`ON DELETE CASCADE` means that deleting a user from the database also deletes that
user's devices.

The file follows Flyway's usual naming format:

```text
V<version>__<description>.sql
```

The `V1` means version 1, and the two underscores separate the version from the
description.

### Important: migration is not currently automatic

Although the SQL file is stored under the conventional `db/migration` directory, the
current `pom.xml` does **not** include the Flyway dependency. Therefore this service
does not automatically execute `V1__device_table.sql` at startup.

The table must already exist before the application starts, or the migration must be
applied manually. The foreign key also assumes that the shared `lightwise` database
already contains the `user` table, normally created by the user service migration.

For a local database, the SQL can be applied manually from a MySQL client:

```bash
mysql -u root -p lightwise < src/main/resources/db/migration/V1__device_table.sql
```

If Flyway should manage migrations automatically in the future, the project would need
the Spring Boot Flyway starter and the MySQL Flyway support dependency in `pom.xml`.
New schema changes should then be added as new migrations such as `V2__...sql`, rather
than editing an already-applied migration.

Create the database if it does not exist:

```sql
CREATE DATABASE lightwise
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

## Running the application

Requirements:

- Java 21
- MySQL
- A MySQL database named `lightwise`
- A `user` table if the device foreign key is enabled
- The datasource username and password configured in `application.properties`

From the `device-service` directory:

On Linux/macOS:

```bash
./mvnw spring-boot:run
```

On Windows:

```bat
mvnw.cmd spring-boot:run
```

The API is available at:

```text
http://localhost:8081/api/v1/devices
```

The OpenAPI/Swagger UI supplied by Springdoc is normally available at:

```text
http://localhost:8081/swagger-ui/index.html
```

## Building the application

```bash
./mvnw clean package
```

This compiles the source code, runs enabled tests, and creates a packaged JAR in the
`target` directory.

Run the packaged application with:

```bash
java -jar target/device-service-0.0.1-SNAPSHOT.jar
```

## Tests

Tests are located under `src/test/java`.

### `DeviceServiceApplicationTests.java`

`@SpringBootTest` starts the Spring application context for the test. The enabled
`contextLoads` test checks that the application can start and that its Spring beans,
JPA configuration, and database connection can be initialized.

The test class also contains `createDevices`, which creates 100 sample devices for ten
users. It is marked with `@Disabled`, so it does not run during normal test execution.
This prevents an ordinary test run from inserting sample data into the configured
database.

Run enabled tests with:

```bash
./mvnw test
```

Because the test starts the configured Spring context, MySQL must be running and the
database credentials must be correct. The disabled seeding method uses
`DeviceType.values()` to choose device types and assigns devices to user IDs from 1
through 10.

## Maven build file

### `pom.xml`

Maven is responsible for dependency management and the build lifecycle. The POM
declares:

- Spring Boot 4.1.1 as the parent
- Java 21
- JPA and web dependencies
- MySQL runtime support
- OpenAPI documentation support
- Test dependencies
- The Spring Boot Maven plugin
- Lombok annotation processing for main and test compilation

`runtime` dependencies are needed when the application runs. `test` dependencies are
used only while compiling and running tests. Lombok is optional because it mainly
generates source code during compilation.

## Current behavior and limitations

- There is no Bean Validation such as `@NotBlank` or `@NotNull`, so malformed request
  data is not rejected by validation yet.
- There is no authentication or authorization.
- `userId` is stored as a number; the service does not verify through an API call that
  the user exists.
- The create endpoint is `/api/v1/devices/create`, rather than the more conventional
  `/api/v1/devices`.
- The create endpoint currently returns `200 OK`, not `201 Created`.
- The service does not use explicit `@Transactional` annotations.
- Flyway is not included in the current dependencies, so the migration SQL is not
  automatically applied.
- The database migration uses a foreign key to the `user` table, so user schema setup
  must happen before device schema setup.

