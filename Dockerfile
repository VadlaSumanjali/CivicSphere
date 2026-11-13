FROM maven:3.9.8-eclipse-temurin-21-alpine AS builder
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S civicsphere && adduser -S civicsphere -G civicsphere
RUN mkdir -p /tmp/civicsphere-uploads /app/civicsphere-data && chown -R civicsphere:civicsphere /tmp/civicsphere-uploads /app
COPY --from=builder /build/target/civicsphere.jar app.jar
RUN chown civicsphere:civicsphere app.jar
USER civicsphere
ENV PORT=8080
ENV CIVICSPHERE_UPLOAD_DIR=/tmp/civicsphere-uploads
ENV CIVICSPHERE_DATA_DIR=/app/civicsphere-data
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT:-8080} -Djava.security.egd=file:/dev/./urandom -jar app.jar"]