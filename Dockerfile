FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
<<<<<<< HEAD
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY src src
RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre
RUN groupadd --system appgroup && useradd --system --gid appgroup --create-home appuser
WORKDIR /app
COPY --from=build /app/target/*.war /app/app.war
RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app
USER appuser
EXPOSE 8080
=======

COPY pom.xml mvnw ./
COPY .mvn .mvn

RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src

RUN ./mvnw clean package -DskipTests

FROM eclipse-temurin:17-jre

RUN groupadd --system appgroup && useradd --system --gid appgroup --create-home appuser

WORKDIR /app

COPY --from=build /app/target/*.war /app/app.war

RUN mkdir -p /app/uploads && chown -R appuser:appgroup /app

USER appuser

EXPOSE 8080

>>>>>>> 5fc493a92115f21ee289cee34af6b9c07694d03b
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.war"]