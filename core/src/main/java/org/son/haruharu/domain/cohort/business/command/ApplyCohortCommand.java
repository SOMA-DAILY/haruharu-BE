package org.son.haruharu.domain.cohort.business.command;

import org.son.haruharu.domain.cohort.implement.ApplicationForm;

import java.util.Map;

/** 기수 신청. 학교·학과·번호와 트랙 추가 문항의 답(문항 key → 답). */
public record ApplyCohortCommand(String school, String department, String phone, Map<String, String> answers) {

    public ApplicationForm toForm() {
        return new ApplicationForm(school, department, phone, answers);
    }
}
