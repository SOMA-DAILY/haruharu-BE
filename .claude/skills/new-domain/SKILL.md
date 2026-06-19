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

```
src/main/java/org/son/haruharu/domain/{domain}/
├── application/
│   ├── {Domain}Controller.java
│   └── dto/
│       └── .gitkeep
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
│   ├── {Domain}Reader.java
│   ├── {Domain}Writer.java
│   └── exception/
│       └── .gitkeep
└── storage/
    ├── {Domain}Entity.java
    └── {Domain}JpaRepository.java

src/test/java/org/son/haruharu/domain/{domain}/
└── business/
    └── {Domain}ServiceTest.java
```

### Step 3. 스텁 파일 생성

패키지명은 `org.son.haruharu.domain.{domain}` 사용.

**`{Domain}Controller.java`**
```java
package org.son.haruharu.domain.{domain}.application;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.utils.ApiResponse;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{domain}")
@RequiredArgsConstructor
public class {Domain}Controller {
    private final {Domain}Service {domain}Service;
}
```

**`{Domain}Service.java`**
```java
package org.son.haruharu.domain.{domain}.business;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.{domain}.implement.{Domain}Reader;
import org.son.haruharu.domain.{domain}.implement.{Domain}Writer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class {Domain}Service {
    private final {Domain}Reader {domain}Reader;
    private final {Domain}Writer {domain}Writer;
}
```

**`{Domain}.java`** — 순수 도메인 객체 (JPA 없음)
```java
package org.son.haruharu.domain.{domain}.implement;

public class {Domain} {
}
```

**`{Domain}Reader.java`**
```java
package org.son.haruharu.domain.{domain}.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.{domain}.storage.{Domain}JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class {Domain}Reader {
    private final {Domain}JpaRepository {domain}JpaRepository;
}
```

**`{Domain}Writer.java`**
```java
package org.son.haruharu.domain.{domain}.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.{domain}.storage.{Domain}JpaRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class {Domain}Writer {
    private final {Domain}JpaRepository {domain}JpaRepository;
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
import org.son.haruharu.domain.{domain}.implement.{Domain};

@Entity
@Table(name = "{domain}")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class {Domain}Entity {

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

**`{Domain}ServiceTest.java`**
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

컴파일 오류가 있으면 수정한다. 스텁 파일의 `UnsupportedOperationException`은 정상.

### Step 6. 완료 보고

생성된 파일 목록을 출력하고 다음 안내:
- "이제 `/bdd-implement #{이슈번호}` 로 구현을 시작하세요."
