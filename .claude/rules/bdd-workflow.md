# BDD 개발 워크플로우

환각을 없애기 위해 **의도(시나리오) → 테스트 → 구현 → 실제 검증** 순서를 지킨다.

## 5단계 개발 사이클

### 1. 시나리오 확인 (GitHub Issue)
작업 시작 전 Issue의 Given-When-Then 시나리오를 반드시 읽는다.
시나리오 없으면 `/create-issue`로 먼저 작성.

```
Given: 회원가입한 유저가 있다
When:  유효한 카카오 OAuth 코드로 로그인 요청 시
Then:  AccessToken/RefreshToken이 HttpOnly 쿠키에 설정되고 200 반환
```

### 2. 테스트 작성 (RED)
시나리오를 그대로 테스트로 변환. **구현 전에 테스트 먼저.**

```java
@Test
@DisplayName("유효한 카카오 코드로 로그인 시 JWT 쿠키가 반환된다")
void givenValidKakaoCode_whenLogin_thenReturnsJwtCookies() {
    // given
    given(kakaoOAuthPort.getUserInfo("valid-code"))
        .willReturn(new KakaoUserInfo("12345", "user@test.com"));
    // when
    var result = authService.loginWithKakao(new KakaoLoginCommand("valid-code"));
    // then
    assertThat(result.accessToken()).isNotEmpty();
}
```

### 3. 최소 구현 (GREEN)
테스트를 통과하는 최소한의 구현. 불필요한 추상화 금지.

### 4. 실제 검증 (VERIFY)
`./gradlew bootRun` 후 실제 API 호출로 시나리오 충족 확인.
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- WebFetch MCP로 직접 HTTP 호출
- **이 단계 없이 완료 처리 금지.**

### 5. 리팩토링 (REFACTOR)
테스트 통과 상태에서 코드 정리.

## 구현 완료 조건 (체크리스트)

- [ ] `./gradlew test` 전체 통과
- [ ] 서버 기동 성공 (`./gradlew bootRun`)
- [ ] 실제 API 호출로 BDD 시나리오 충족 확인
- [ ] `./gradlew lint` 경고 없음 (또는 인지된 경고만)