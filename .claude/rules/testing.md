---
paths:
  - "**/src/test/**"
---

# Testing Strategy

## Framework
- JUnit 5 + AssertJ BDD (`given/willReturn`, `assertThat`)
- Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`)
- Testcontainers (PostgreSQL 통합 테스트)

## 반드시 작성하는 테스트

| 대상 | 테스트 유형 | 이유 |
|------|-------------|------|
| `Service` 비즈니스 로직 | 단위 | 모든 분기 + 예외 케이스 |
| 도메인 객체 불변식/유효성 | 단위 | 도메인 규칙 오염 방지 |
| 핵심 유저 플로우 | 통합 | 계층 간 연동 버그 포착 |

## 작성하지 않는 테스트

- Controller 단순 위임 코드
- Repository 단순 CRUD (JPA 보장)
- 설정 클래스, DTO 생성자/getter

## 단위 테스트 패턴

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {
    @Mock UserRepository userRepository;
    @Mock UserReader userReader;
    UserService userService;

    @BeforeEach void setUp() {
        userService = new UserService(userRepository, userReader);
    }

    @Test
    @DisplayName("존재하지 않는 유저 ID 조회 시 예외가 발생한다")
    void givenNonExistentId_whenFindById_thenThrowsException() {
        // given
        given(userReader.getByUuid(any())).willThrow(new UserNotFoundException("id"));
        // when & then
        assertThatThrownBy(() -> userService.findById(UUID.randomUUID()))
            .isInstanceOf(UserNotFoundException.class);
    }
}
```

## 통합 테스트 패턴

```java
@SpringBootTest
@Testcontainers
class AuthFlowIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);
    }
}
```

## 테스트 파일 위치

```
{모듈}/src/test/java/org/son/haruharu/     # 모듈: core / app / admin
  domain/{domain}/
    business/     ← Service 단위 테스트
    implement/    ← 도메인 객체 단위 테스트
    storage/      ← Repository 통합 테스트
  integration/    ← 유저 플로우 end-to-end
```

## 명명 규칙

```java
// 메서드명
void given{조건}_when{행동}_then{결과}()

// @DisplayName (한국어)
@DisplayName("만료된 토큰으로 갱신 요청 시 InvalidTokenException이 발생한다")
```

## Coverage

- 목표: Service + Domain 계층 80%+
- `./gradlew jacocoTestReport` → `build/reports/jacoco/`