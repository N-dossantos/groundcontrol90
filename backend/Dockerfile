FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Usuario sin privilegios: si alguien consigue ejecución de código dentro del
# contenedor, no arranca siendo root. El directorio de uploads se crea acá y se le
# da al usuario porque es lo único en lo que la app escribe (el volumen se monta
# encima en producción y hereda el owner del punto de montaje).
RUN groupadd --system --gid 1001 spring \
 && useradd --system --uid 1001 --gid spring spring \
 && mkdir -p /app/uploads \
 && chown -R spring:spring /app
USER spring

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
