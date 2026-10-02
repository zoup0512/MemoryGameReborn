#syntax=docker/dockerfile:1
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY build/libs/*-boot.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar", "--spring.profiles.active=postgres"]
