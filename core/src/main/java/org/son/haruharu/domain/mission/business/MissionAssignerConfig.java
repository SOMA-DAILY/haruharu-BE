package org.son.haruharu.domain.mission.business;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 활성 배정 정책을 설정으로 고른다.
 * <p>
 * 두 정책을 모두 빈으로 등록하되 주입 대상은 {@link Primary} 하나로 정한다. {@code RANDOM_V1} 은
 * 지우지 않는다 — Rule V1 의 효과를 판단하려면 같은 지표를 내는 대조군이 필요하고, 기록된 propensity 로
 * 오프라인 비교도 해야 한다.
 * <p>
 * 정책을 바꾸면 배정 로그의 {@code policy_version} 이 달라지므로 이후 분석에서 구간을 나눌 수 있다.
 */
@Configuration
public class MissionAssignerConfig {

    private static final Logger log = LoggerFactory.getLogger(MissionAssignerConfig.class);

    @Bean
    public RandomAssignerV1 randomAssignerV1() {
        return new RandomAssignerV1();
    }

    @Bean
    public RuleBasedAssignerV1 ruleBasedAssignerV1() {
        return new RuleBasedAssignerV1();
    }

    @Bean
    @Primary
    public MissionAssigner activeMissionAssigner(
            @Value("${haruharu.mission.assignment.policy:RULE_V1}") String policy,
            RandomAssignerV1 randomAssigner,
            RuleBasedAssignerV1 ruleAssigner
    ) {
        MissionAssigner active = RandomAssignerV1.POLICY_VERSION.equalsIgnoreCase(policy)
                ? randomAssigner
                : ruleAssigner;
        log.atInfo()
                .addKeyValue("event", "MISSION_ASSIGNER_SELECTED")
                .addKeyValue("policy", policy)
                .addKeyValue("assigner", active.getClass().getSimpleName())
                .log("미션 배정 정책 선택");
        return active;
    }
}
