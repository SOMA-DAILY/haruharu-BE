package org.son.haruharu.domain.identity.business.dto;

import java.time.LocalDate;

/** 본인인증(PASS)에서 확정된 성별·생년월일. 성별/나이의 단일 출처(SSOT)로 사용한다. */
public record VerifiedIdentity(String gender, LocalDate birthday) {}
