# 멀티모듈: MODULE=app(유저 서버) 또는 MODULE=admin(어드민 서버)로 빌드 대상 선택.
# 예) docker build --build-arg MODULE=admin -t haruharu-admin .
ARG MODULE=app

FROM eclipse-temurin:21-jdk-alpine AS builder
ARG MODULE
WORKDIR /app
COPY gradlew .
COPY gradle gradle
COPY build.gradle settings.gradle ./
COPY core core
COPY app app
COPY admin admin
RUN chmod +x gradlew && ./gradlew :${MODULE}:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
WORKDIR /app
COPY --from=builder /app/${MODULE}/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
