FROM openjdk:17-jdk-slim as builder

WORKDIR /app
COPY . /app
RUN ./mvnw clean package -DskipTests

FROM openjdk:17-jdk-slim

# Copy the jar from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Set JVM options for better performance
ENV JAVA_OPTS="-server -XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+UseStringDeduplication -XX:+UseCompressedOops -XX:+DisableExplicitGC -XX:+ParallelRefProcEnabled -XX:MaxRAMPercentage=80 -XX:InitialRAMPercentage=70"

# Expose the service port
EXPOSE 8181
EXPOSE 8000

# Run the service
ENTRYPOINT java $JAVA_OPTS -jar app.jar