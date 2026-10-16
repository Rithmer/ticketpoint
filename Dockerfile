FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY core core
COPY backend backend
COPY javafx-client/pom.xml javafx-client/pom.xml
RUN javac -d /probe backend/src/main/java/ru/ticketpoint/backend/HealthProbe.java
RUN chmod +x mvnw && ./mvnw -B -pl backend -am -DskipTests package
FROM eclipse-temurin:21-jre-noble
RUN groupadd --system ticketpoint && useradd --system --gid ticketpoint --home-dir /app --no-create-home ticketpoint
WORKDIR /app
COPY --from=build /build/backend/target/backend-1.0.0.jar /app/backend.jar
COPY --from=build /probe /app/healthcheck
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=6 CMD ["java", "-cp", "/app/healthcheck", "ru.ticketpoint.backend.HealthProbe"]
USER ticketpoint
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
