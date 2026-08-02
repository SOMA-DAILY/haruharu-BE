---
description: 새 도메인의 4계층 패키지 스캐폴딩. 새로운 비즈니스 도메인 추가 시 사용. org.son.haruharu 패키지 기준으로 application/business/implement/storage 구조와 스텁 파일을 일괄 생성.
argument-hint: "[도메인명]"
allowed-tools: Write Bash(mkdir *) Bash(./gradlew *) Read Agent
---

## 언제 쓰나?

- 새 비즈니스 도메인 추가 시 (예: `payment`, `notification`, `post`)
- `/bdd-implement`가 새 도메인 필요 감지 시 자동 호출
- 도메인 이름만 있으면 됨 — 이슈 없어도 실행 가능

## 실행 절차

### Step 1. 도메인 이름 변환

인자로 받은 도메인 이름을 camelCase/PascalCase로 변환한다.
예: `user` → `User`, `kakaoAuth` → `KakaoAuth`

도메인명: `$ARGUMENTS`

### Step 2. 디렉토리 구조 생성

`.claude/rules/architecture.md` 기준으로 아래 구조를 생성한다.

**모듈 배치 (Gradle 멀티모듈)**

| 계층 | 모듈 | 이유 |
|------|------|------|
| `business` · `implement` · `storage` | `core` | `app`·`admin`이 공유하는 도메인 로직 |
| `application` (Controller·Request DTO) | `app` | core는 웹 계층을 모른다 |
| 테스트 | `app/src/test` | 현행 관례 — 도메인 테스트는 전부 실행 모듈에 둔다 |

베이스 패키지는 두 모듈 모두 `org.son.haruharu`로 동일하다 (`admin`만 `org.son.haruharu.admin`).
어드민 전용 기능이면 이 스킬 대신 `admin/domain/{domain}/`에 4계층 전부를 만든다.

```
core/src/main/java/org/son/haruharu/domain/{domain}/
├── business/
│   ├── {Domain}Service.java
│   ├── command/
│   │   └── .gitkeep
│   ├── dto/
│   │   └── .gitkeep
│   └── exception/
│       └── .gitkeep
├── implement/
│   ├── {Domain}.java
│   ├── {Domain}Repository.java
│   ├── {Domain}Reader.java
│   ├── {Domain}Writer.java
│   └── exception/
│       └── .gitkeep
└── storage/
    ├── {Domain}Entity.java
    ├── {Domain}JpaRepository.java
    ├── {Domain}RepositoryImpl.java
    └── exception/
        └── .gitkeep

app/src/main/java/org/son/haruharu/domain/{domain}/
└── application/
    ├── {Domain}Controller.java
    └── dto/
        └── .gitkeep

app/src/test/java/org/son/haruharu/domain/{domain}/
└── business/
    └── {Domain}ServiceTest.java
```

### Step 3. 스텁 파일 생성

패키지명은 모듈이 달라도 동일하게 `org.son.haruharu.domain.{domain}` 을 쓴다
(`core`와 `app`이 같은 베이스 패키지를 공유하므로 컴포넌트 스캔이 그대로 동작한다).

> **모듈이 다르면 import는 필수다.** `app`의 Controller에서 `core`의 Service·DTO를 쓸 때
> 같은 `org.son.haruharu.domain.{domain}` 아래라도 하위 패키지가 다르므로 반드시 import한다.

**`{Domain}Controller.java`** — `app` 모듈
```java
package org.son.haruharu.domain.{domain}.application;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.domain.{domain}.business.{Domain}Service;   // core 모듈 — import 필수
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "{도메인 한글명}", description = "{도메인 설명}")
@RestController
@RequestMapping("/api/v1/{domain}s")
public class {Domain}Controller {

    private final {Domain}Service {domain}Service;

    public {Domain}Controller({Domain}Service {domain}Service) {
        this.{domain}Service = {domain}Service;
    }

    // 응답은 org.son.haruharu.common.ApiResponse 로 감싼다 (core 모듈).
    // @Operation(summary = "...", description = "...")
    // @GetMapping
    // public ApiResponse<{Domain}Response> get(Authentication authentication) { ... }
}
```

**`{Domain}Service.java`** — `core` 모듈
```java
package org.son.haruharu.domain.{domain}.business;

import org.son.haruharu.domain.{domain}.implement.{Domain}Reader;
import org.son.haruharu.domain.{domain}.implement.{Domain}Writer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class {Domain}Service {

    private final {Domain}Reader {domain}Reader;
    private final {Domain}Writer {domain}Writer;

    public {Domain}Service({Domain}Reader {domain}Reader, {Domain}Writer {domain}Writer) {
        this.{domain}Reader = {domain}Reader;
        this.{domain}Writer = {domain}Writer;
    }

    // 읽기 메서드: 클래스 기본 readOnly = true 상속
    // 쓰기 메서드: @Transactional 개별 추가
    // @Transactional
    // public {Domain}Response create({Domain}CreateCommand command) { ... }
}
```

아래 implement·storage 스텁은 모두 `core` 모듈에 생성한다.

**`{Domain}.java`** — 순수 도메인 객체 (JPA 없음)
```java
package org.son.haruharu.domain.{domain}.implement;

public class {Domain} {
}
```

**`{Domain}Repository.java`** — 포트 인터페이스
```java
package org.son.haruharu.domain.{domain}.implement;

import java.util.Optional;

public interface {Domain}Repository {
    Optional<{Domain}> findById(Long id);
    {Domain} save({Domain} {domain});
    void deleteById(Long id);
}
```

**`{Domain}Reader.java`**
```java
package org.son.haruharu.domain.{domain}.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class {Domain}Reader {
    private final {Domain}Repository {domain}Repository;
}
```

**`{Domain}Writer.java`**
```java
package org.son.haruharu.domain.{domain}.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class {Domain}Writer {
    private final {Domain}Repository {domain}Repository;
}
```

**`{Domain}Entity.java`**
```java
package org.son.haruharu.domain.{domain}.storage;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.{domain}.implement.{Domain};

// BaseEntity 상속: createdAt/updatedAt 자동 관리 + deleted_at 기반 전역 soft-delete.
// 감사 컬럼도 soft-delete도 불필요한 예외 케이스만 상속을 뺀다.
@Entity
@Table(name = "{domain}s")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class {Domain}Entity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public static {Domain}Entity from({Domain} domain) {
        throw new UnsupportedOperationException("미구현");
    }

    public {Domain} toDomain() {
        throw new UnsupportedOperationException("미구현");
    }
}
```

**`{Domain}JpaRepository.java`**
```java
package org.son.haruharu.domain.{domain}.storage;

import org.springframework.data.jpa.repository.JpaRepository;

public interface {Domain}JpaRepository extends JpaRepository<{Domain}Entity, Long> {
}
```

**`{Domain}RepositoryImpl.java`** — DIP 구현체
```java
package org.son.haruharu.domain.{domain}.storage;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.{domain}.implement.{Domain};
import org.son.haruharu.domain.{domain}.implement.{Domain}Repository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class {Domain}RepositoryImpl implements {Domain}Repository {
    private final {Domain}JpaRepository jpaRepository;

    @Override
    public Optional<{Domain}> findById(Long id) {
        return jpaRepository.findById(id).map({Domain}Entity::toDomain);
    }

    @Override
    public {Domain} save({Domain} {domain}) {
        return jpaRepository.save({Domain}Entity.from({domain})).toDomain();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }
}
```

**`{Domain}ServiceTest.java`** — `app/src/test` (테스트 대상 Service는 `core`에 있다)
```java
package org.son.haruharu.domain.{domain}.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.{domain}.implement.{Domain}Reader;
import org.son.haruharu.domain.{domain}.implement.{Domain}Writer;

@ExtendWith(MockitoExtension.class)
class {Domain}ServiceTest {

    @Mock private {Domain}Reader {domain}Reader;
    @Mock private {Domain}Writer {domain}Writer;

    private {Domain}Service {domain}Service;

    @BeforeEach
    void setUp() {
        {domain}Service = new {Domain}Service({domain}Reader, {domain}Writer);
    }

    @Test
    @DisplayName("{Domain}Service 기본 테스트")
    void basicTest() {
        // given
        // when
        // then
    }
}
```

### Step 4. Reviewer 에이전트로 규칙 검증

`domain-reviewer` 에이전트를 실행해 생성된 파일 전체를 검증한다.

```
Agent: domain-reviewer
입력: 생성된 파일 경로 목록 + 도메인명
```

**FAIL 항목이 있으면 즉시 수정 후 재검증.** PASS 확인 전 다음 단계로 넘어가지 않는다.

### Step 5. 빌드 검증

```bash
./gradlew build
```

멀티모듈이므로 루트에서 실행해 `core`·`app`·`admin`을 함께 검증한다.
빠르게 확인하려면 `./gradlew :core:compileJava :app:compileJava`.

컴파일 오류가 있으면 수정한다. 스텁 파일의 `UnsupportedOperationException`은 정상.
**모듈 간 import 누락이 이 단계에서 가장 흔한 실패다** — `app`의 Controller가 `core`의 Service를 import했는지 확인.

### Step 6. 완료 보고

생성된 파일 목록을 출력하고 다음 안내:
- "이제 `/bdd-implement #{이슈번호}` 로 구현을 시작하세요."

## Anti-Patterns (금지)

- application 계층을 `core` 모듈에 생성 (Controller는 `app` 전용 — core는 웹 계층을 모른다)
- Entity·Repository·Service를 `app` 모듈에 생성 (`admin`이 못 쓴다 — 도메인은 전부 `core`)
- `core`에 필터·스케줄러·`@SpringBootApplication` 추가 (admin에 cron·STOMP가 딸려온다)
- 모듈이 다른데 import 없이 클래스 참조 (같은 베이스 패키지라도 하위 패키지가 다르면 import 필수)
- 스텁 단계에서 비즈니스 로직 미리 구현 (스캐폴딩은 구조만, 로직은 /bdd-implement에서)
- domain-reviewer PASS 없이 완료 보고