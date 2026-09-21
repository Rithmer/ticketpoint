FROM eclipse-temurin:17-jre-noble
RUN groupadd --system ticketpoint && useradd --system --gid ticketpoint --home-dir /app --no-create-home ticketpoint
WORKDIR /app
COPY target/ticketpoint-1.0-SNAPSHOT.jar /app/ticketpoint.jar
RUN mkdir -p /app/logs /app/exports && chown -R ticketpoint:ticketpoint /app
USER ticketpoint
ENTRYPOINT ["java", "-jar", "/app/ticketpoint.jar"]
