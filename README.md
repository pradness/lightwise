# LightWise

Start:

```bash
docker compose -v up -d
```

```bash
mvn spring-boot:run -pl api-gateway -am
mvn spring-boot:run -pl user-service -am
mvn spring-boot:run -pl device-service -am
mvn spring-boot:run -pl ingestion-service -am
mvn spring-boot:run -pl usage-service -am
mvn spring-boot:run -pl alert-service -am
mvn spring-boot:run -pl insight-service -am
```

Stop:

```bash
docker compose down
```
