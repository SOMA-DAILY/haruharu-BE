# 멀티모듈: MODULE=app(유저 서버) 또는 MODULE=admin(어드민 서버)로 빌드 대상 선택.
# 예) docker build --build-arg MODULE=admin -t haruharu-admin .
ARG MODULE=app

# 빌더는 빌드 호스트 아키텍처($BUILDPLATFORM)에서 네이티브로 컴파일한다.
# jar(자바 바이트코드)는 arch 중립이므로, arm64 타깃이라도 x86 러너에서 QEMU 에뮬레이션 없이
# 네이티브로 빌드해 속도를 확보한다. 최종 런타임 이미지만 타깃(arm64)으로 굽는다.
FROM --platform=$BUILDPLATFORM eclipse-temurin:21-jdk-alpine AS builder
ARG MODULE
WORKDIR /app

# 1) 빌드 스크립트만 먼저 복사 → 의존성 다운로드를 소스와 분리된 레이어로 캐시한다.
#    build.gradle 류가 안 바뀌면 코드만 바뀐 빌드에서 의존성 재다운로드를 건너뛴다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY core/build.gradle core/
COPY app/build.gradle app/
COPY admin/build.gradle admin/
RUN chmod +x gradlew && ./gradlew :${MODULE}:dependencies --no-daemon || true

# 2) 소스 복사 후 실제 빌드 (의존성은 위 캐시 레이어에서 재사용).
COPY core core
COPY app app
COPY admin admin
RUN ./gradlew :${MODULE}:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-alpine
ARG MODULE
ARG APPLICATION_COMMIT_SHA=unknown
WORKDIR /app
COPY --from=builder /app/${MODULE}/build/libs/*.jar app.jar
EXPOSE 8080

# 로그 타임스탬프를 KST로 남긴다(#257). TimeZoneConfig(@PostConstruct)는 Logback 초기화보다 늦게 실행돼
# 로그에는 컨테이너 기본 타임존(UTC)이 캐싱된다 — 앱 로직은 KST인데 로그만 9시간 어긋나는 원인.
# JVM 기동 전에 정해져야 하므로 여기서 지정한다. TimeZoneConfig는 로컬·테스트 방어용으로 존치.
ENV TZ=Asia/Seoul \
    APPLICATION_COMMIT_SHA=${APPLICATION_COMMIT_SHA}

ENTRYPOINT ["java", "-jar", "app.jar"]
