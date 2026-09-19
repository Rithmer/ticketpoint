FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S ticketpoint && adduser -S -G ticketpoint ticketpoint
WORKDIR /app
COPY target/ticketpoint-1.0-SNAPSHOT.jar /app/ticketpoint.jar
RUN mkdir -p /app/logs /app/exports && chown -R ticketpoint:ticketpoint /app
USER ticketpoint
ENTRYPOINT ["java", "-jar", "/app/ticketpoint.jar"]
