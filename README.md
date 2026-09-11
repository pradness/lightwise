Start:
```
docker compose -v up -d
```

```
mvn spring-boot:run -pl user-service -am
mvn spring-boot:run -pl device-service -am

```

Stop:
```
docker compose down
```