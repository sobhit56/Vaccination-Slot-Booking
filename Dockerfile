# ==============================================================================
# VaxSlot Connect - Production Dockerfile for Microsoft Azure App Service
# Multi-stage lightweight build using official Eclipse Temurin JDK/JRE
# ==============================================================================

# Build Stage
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy project files
COPY src/ ./src/
COPY web/ ./web/
COPY data/ ./data/

# Compile pure Java backend into bytecode
RUN mkdir -p bin && javac -d bin src/model/*.java src/dsa/*.java src/util/*.java src/service/*.java src/Main.java

# Runtime Stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/bin ./bin
COPY --from=builder /app/web ./web
COPY --from=builder /app/data ./data

# Azure dynamically exposes the port via the PORT environment variable
ENV PORT=8080
EXPOSE 8080

# Run the pure Java server
CMD ["java", "-cp", "bin", "Main"]
