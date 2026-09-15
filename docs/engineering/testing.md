# Testing Strategy

## Framework
- JUnit 5 + AssertJ BDD (`given/willReturn`, `assertThat`)
- Mockito (`@ExtendWith(MockitoExtension.class)`, `@Mock`)
- Testcontainers (PostgreSQL 통합 테스트)

## 반드시 작성하는 테스트

| 대상 | 테스트 유형 | 이유 |
|------|-------------|------|
| `Service` 비즈니스 로직 | 단위 | 변경된 동작 + 관련 예외·경계 조건 |
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


## 실행 범위와 검증 증거

- 개발 중에는 관련 테스트 클래스나 모듈부터 실행한다. 예: `./gradlew :app:test --tests '*AuthServiceTest'`. 테스트가 있는 모듈은 기존 파일로 확인한다.
- 중요한 업무 규칙과 버그 수정은 red/green TDD를 사용한다. 실패가 기대한 행동 때문인지 확인하고 구현한다. 문서·기계적 수정에는 새 테스트를 억지로 만들지 않는다.
- API 계약·필터·직렬화·외부 연동·기동 설정 변경은 관련 통합 테스트와 로컬 서버 HTTP 확인을 포함한다. 내부 로직 수정에는 서버 기동을 일률적으로 요구하지 않는다.
- DB·락·동시성 변경은 PostgreSQL 통합 테스트에서 제약, 트랜잭션, 경쟁 조건을 확인한다. 목 객체만으로 검증을 대체하지 않는다.
- Java·빌드·런타임 변경은 PR 준비 시 전체 테스트와 커버리지를 확인한다. CI의 전체 build 검증과 기존 JaCoCo 기준을 유지한다.
- 일반 문서 변경은 수정한 내용·링크·예제를 확인한다. 작업 지침·스킬·리뷰어 변경은 관련 참조·메타데이터·호스트 연결을 검사하고, 절차 변경은 대표 요청으로 판단을 확인한다.
- 훅 실행·등록·보호 경로 변경은 관련 JSON/TOML 파싱, 실제 허용·차단과 호스트 호환성을 검사한다. 검사는 실제로 영향받는 구성에 한정한다.
- 재사용할 증거에는 명령, 대상, 종료 상태·요약, 코드 상태와 환경을 남긴다. HEAD뿐 아니라 작업 트리 diff와 관련 미추적 파일의 내용도 확인한다.
- 입력 코드·테스트·설정·도구 버전·DB 상태·환경이 같을 때 통과한 증거를 재사용한다. 이후 변경이 영향을 주는 검사는 다시 실행하고, 영향이 불명확하면 범위를 넓힌다.
- 실패하면 원인을 수정하고 실패한 검사 및 영향받는 검사를 재실행한다. 관련 없는 통과 검사를 처음부터 반복하지 않는다.
- 미실행·환경 제약·미해당을 PASS와 구분한다. 필요한 검증이 남으면 완료 또는 PR 준비 완료로 보고하지 않는다.
