package org.son.haruharu.domain.mission.business;

import org.son.haruharu.domain.mission.business.exception.NoMissionCandidateException;
import org.son.haruharu.domain.mission.implement.MissionAssignmentContextSnapshot;
import org.son.haruharu.domain.mission.implement.MissionAssignmentRelaxationLevel;
import org.son.haruharu.domain.mission.implement.MissionAttributeLevel;
import org.son.haruharu.domain.mission.implement.MissionCandidateSnapshot;
import org.son.haruharu.domain.mission.implement.MissionGoal;
import org.son.haruharu.domain.mission.implement.MissionRevision;
import org.son.haruharu.domain.mission.implement.PreferenceLeakRisk;
import org.son.haruharu.domain.mission.implement.RoomMission;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Day 별 자기개방 상한과 안전성을 정책 레벨에서 강제하고, 부담·다양성을 점수로 반영하는 배정 정책.
 * <p>
 * {@code RANDOM_V1} 은 안전성을 eligibility 데이터에만 의존한다. 운영자가 실수로 깊은 미션에 Day 1
 * eligibility 를 주면 그대로 배정된다. 이 정책은 그것을 코드로 막는다.
 * <p>
 * 방 상태는 컨텍스트에 실려 오지만 <b>점수에 쓰지 않는다</b>. 임계값을 정할 데이터가 없기도 하지만,
 * 방 상태가 선택에 영향을 주지 않아야 {@code 방 상태 → 선택 → 성과} 교란 없이 Rule V2 의 근거를
 * 모을 수 있기 때문이다.
 */
public class RuleBasedAssignerV1 implements MissionAssigner {

    static final String POLICY_VERSION = "RULE_V1";
    static final String CANDIDATE_SCHEMA_VERSION = "candidate-v2";
    static final String RNG_ALGORITHM = "SPLITTABLE_RANDOM_V1";

    private static final int PROBABILITY_SCALE = 12;
    private static final int SCORE_SCALE = 6;

    /** Day 별 허용 자기개방 상한. 상한이지 목표치가 아니다. 완화 불가. */
    static final Map<Integer, MissionAttributeLevel> MAX_DISCLOSURE_BY_DAY = Map.of(
            1, MissionAttributeLevel.LOW,
            2, MissionAttributeLevel.MEDIUM,
            3, MissionAttributeLevel.HIGH);

    /** Day 별 권장 미션 목표. 맞으면 가점. */
    static final Map<Integer, Set<MissionGoal>> PREFERRED_GOALS_BY_DAY = Map.of(
            1, Set.of(MissionGoal.OBSERVATION, MissionGoal.PREFERENCE),
            2, Set.of(MissionGoal.MEANING, MissionGoal.VALUE),
            3, Set.of(MissionGoal.PERSONAL, MissionGoal.RELATIONAL));

    /** 상위 후보 선택 가중치. 1위에 몰되 하위 후보도 관측되도록 남긴다. */
    static final List<BigDecimal> TOP_K_WEIGHTS =
            List.of(new BigDecimal("0.60"), new BigDecimal("0.25"), new BigDecimal("0.15"));

    private static final BigDecimal DAY_FIT_BONUS = new BigDecimal("0.20");
    private static final BigDecimal BURDEN_PENALTY_MEDIUM = new BigDecimal("0.10");
    private static final BigDecimal BURDEN_PENALTY_HIGH = new BigDecimal("0.20");
    private static final BigDecimal GOAL_REPEAT_PENALTY = new BigDecimal("0.15");
    private static final BigDecimal CHANNEL_REPEAT_PENALTY = new BigDecimal("0.05");

    /**
     * 같은 {@code missionGoal} 이 이만큼 연속되면 더 이상 허용하지 않는다.
     * <p>
     * 채널({@code activationChannel})에는 이 제한을 걸지 않는다. 값이 VIDEO/GROUP_CHAT 둘뿐인 데다
     * 카탈로그가 의도적으로 영상 편중(6슬롯 중 5개)이라, 하드 제한을 걸면 세 번째 슬롯부터 항상
     * 완화 단계로 떨어져 완화 기록이 신호가 아니라 잡음이 된다. 게다가 L0 가 늘 비어서 정작 중요한
     * 목표 다양성이 우회된다. 채널 반복은 점수 페널티로만 억제한다.
     */
    static final int GOAL_RUN_LIMIT = 2;

    @Override
    public MissionDecision assignNext(MissionAssignmentContext context) {
        int dayNumber = context.missionSlot().dayNumber();
        Selection selection = select(context, dayNumber);
        if (selection == null) {
            // 후보도 fallback 도 없다. 조용히 넘기면 그 슬롯은 영영 비어 있게 된다.
            throw new NoMissionCandidateException();
        }

        List<Scored> ranked = selection.ranked();
        List<BigDecimal> probabilities = rankWeights(ranked.size());
        int pickedIndex = pick(probabilities, context.rngSeed());
        MissionRevision selected = ranked.get(pickedIndex).revision();

        MissionCandidateSnapshot candidateSnapshot = new MissionCandidateSnapshot(buildCandidates(ranked, probabilities));
        MissionAssignmentContextSnapshot contextSnapshot = new MissionAssignmentContextSnapshot(
                context.room().id(), context.missionSlot().id(), dayNumber,
                context.missionSlot().period().name(), context.missionSlot().opensAt(),
                context.missionSlot().closesAt(), context.previousMissionRevisionIds(),
                context.roomState(), context.contextAsOf());

        long delaySeconds = Math.max(0L,
                Duration.between(context.missionSlot().opensAt(), context.now()).getSeconds());
        UUID explorationId = UUID.nameUUIDFromBytes(
                (context.missionSlot().id() + ":" + context.rngSeed()).getBytes(StandardCharsets.UTF_8));

        RoomMission roomMission = RoomMission.assigned(
                context.room().id(), context.missionSlot().id(), selected.id(),
                context.missionSlot().opensAt(), context.missionSlot().closesAt(), context.sequence(),
                POLICY_VERSION, context.catalogVersion(), context.contextSchemaVersion(),
                context.contextAsOf(), contextSnapshot, CANDIDATE_SCHEMA_VERSION, candidateSnapshot,
                probabilities.get(pickedIndex), explorationId, context.rngSeed(), RNG_ALGORITHM,
                selection.relaxationLevel(), context.applicationCommitSha(), context.now(), delaySeconds);
        return new MissionDecision(roomMission);
    }

    /** 완화 단계를 순서대로 올리며 후보가 생기는 첫 단계를 쓴다. 안전 조건은 어느 단계에서도 유지된다. */
    private Selection select(MissionAssignmentContext context, int dayNumber) {
        List<MissionRevision> fresh = safeOnly(context.candidates(), dayNumber);
        List<MissionRevision> reusable = safeOnly(context.previouslyUsedCandidates(), dayNumber);
        List<MissionRevision> previous = context.previousRevisions();

        List<MissionRevision> clusterOk = fresh.stream()
                .filter(revision -> !repeatsCluster(revision, previous))
                .filter(revision -> !exceedsGoalRun(revision, previous))
                .filter(RuleBasedAssignerV1::withinBurdenLimit)
                .toList();
        if (!clusterOk.isEmpty()) {
            return rank(clusterOk, dayNumber, previous, MissionAssignmentRelaxationLevel.NONE);
        }

        List<MissionRevision> formatOk = fresh.stream()
                .filter(revision -> !exceedsGoalRun(revision, previous))
                .filter(RuleBasedAssignerV1::withinBurdenLimit)
                .toList();
        if (!formatOk.isEmpty()) {
            return rank(formatOk, dayNumber, previous, MissionAssignmentRelaxationLevel.CLUSTER_RELAXED);
        }

        List<MissionRevision> burdenOk = fresh.stream()
                .filter(RuleBasedAssignerV1::withinBurdenLimit)
                .toList();
        if (!burdenOk.isEmpty()) {
            return rank(burdenOk, dayNumber, previous, MissionAssignmentRelaxationLevel.FORMAT_RELAXED);
        }
        if (!fresh.isEmpty()) {
            return rank(fresh, dayNumber, previous, MissionAssignmentRelaxationLevel.BURDEN_RELAXED);
        }
        if (!reusable.isEmpty()) {
            return rank(reusable, dayNumber, previous, MissionAssignmentRelaxationLevel.REUSE_RELAXED);
        }

        List<MissionRevision> fallback = safeOnly(context.fallbackCandidates(), dayNumber);
        if (!fallback.isEmpty()) {
            return rank(fallback, dayNumber, previous, MissionAssignmentRelaxationLevel.SAFE_FALLBACK);
        }
        return null;
    }

    private Selection rank(List<MissionRevision> pool, int dayNumber, List<MissionRevision> previous,
                           MissionAssignmentRelaxationLevel relaxationLevel) {
        List<Scored> ranked = pool.stream()
                .map(revision -> new Scored(revision, score(revision, dayNumber, previous)))
                .sorted(Comparator.comparing(Scored::score).reversed()
                        .thenComparing(scored -> scored.revision().id()))
                .toList();
        return new Selection(ranked, relaxationLevel);
    }

    private List<MissionRevision> safeOnly(List<MissionRevision> pool, int dayNumber) {
        return pool.stream().filter(revision -> satisfiesSafety(revision, dayNumber)).toList();
    }

    /** 어떤 완화 단계에서도 통과해야 하는 조건. 안전성은 후보 부족을 이유로 풀지 않는다. */
    static boolean satisfiesSafety(MissionRevision revision, int dayNumber) {
        MissionAttributeLevel maxDepth = MAX_DISCLOSURE_BY_DAY.getOrDefault(
                dayNumber, MissionAttributeLevel.LOW);
        return revision.disclosureDepth().ordinal() <= maxDepth.ordinal()
                && revision.privacyRisk() != MissionAttributeLevel.HIGH
                && isPreferenceLeakAllowed(revision);
    }

    /** 규칙 기반 적합도. 방 상태는 입력이 아니다. */
    static BigDecimal score(MissionRevision revision, int dayNumber, List<MissionRevision> previous) {
        BigDecimal score = revision.baseQualityScore();
        if (PREFERRED_GOALS_BY_DAY.getOrDefault(dayNumber, Set.of()).contains(revision.missionGoal())) {
            score = score.add(DAY_FIT_BONUS);
        }
        score = score.subtract(switch (revision.participationBurden()) {
            case LOW -> BigDecimal.ZERO;
            case MEDIUM -> BURDEN_PENALTY_MEDIUM;
            case HIGH -> BURDEN_PENALTY_HIGH;
        });
        if (!previous.isEmpty()) {
            MissionRevision last = previous.getLast();
            if (last.missionGoal() == revision.missionGoal()) {
                score = score.subtract(GOAL_REPEAT_PENALTY);
            }
            if (last.activationChannel() == revision.activationChannel()) {
                score = score.subtract(CHANNEL_REPEAT_PENALTY);
            }
        }
        return score.setScale(SCORE_SCALE, RoundingMode.HALF_UP);
    }

    /** 상위 K개에 가중치를 주고 나머지는 0. 후보가 K보다 적으면 정규화한다. */
    private static List<BigDecimal> rankWeights(int size) {
        int weighted = Math.min(size, TOP_K_WEIGHTS.size());
        BigDecimal total = TOP_K_WEIGHTS.subList(0, weighted).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BigDecimal> weights = new ArrayList<>(size);
        BigDecimal assigned = BigDecimal.ZERO;
        for (int index = 0; index < size; index++) {
            if (index >= weighted) {
                weights.add(BigDecimal.ZERO.setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP));
                continue;
            }
            BigDecimal weight = index == weighted - 1
                    // 반올림 잔차를 마지막 가중 후보에 몰아 합이 정확히 1이 되게 한다.
                    ? BigDecimal.ONE.subtract(assigned).setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP)
                    : TOP_K_WEIGHTS.get(index).divide(total, PROBABILITY_SCALE, RoundingMode.HALF_UP);
            weights.add(weight);
            assigned = assigned.add(weight);
        }
        return weights;
    }

    private static int pick(List<BigDecimal> probabilities, long seed) {
        BigDecimal roll = BigDecimal.valueOf(new SplittableRandom(seed).nextDouble())
                .setScale(PROBABILITY_SCALE, RoundingMode.HALF_UP);
        BigDecimal cumulative = BigDecimal.ZERO;
        for (int index = 0; index < probabilities.size(); index++) {
            cumulative = cumulative.add(probabilities.get(index));
            if (roll.compareTo(cumulative) < 0) {
                return index;
            }
        }
        // 부동소수 잔차로 끝까지 못 고른 경우 가중치가 있는 마지막 후보로 되돌린다.
        for (int index = probabilities.size() - 1; index >= 0; index--) {
            if (probabilities.get(index).signum() > 0) {
                return index;
            }
        }
        return 0;
    }

    private static List<MissionCandidateSnapshot.Candidate> buildCandidates(
            List<Scored> ranked, List<BigDecimal> probabilities) {
        List<MissionCandidateSnapshot.Candidate> candidates = new ArrayList<>(ranked.size());
        for (int index = 0; index < ranked.size(); index++) {
            MissionRevision revision = ranked.get(index).revision();
            candidates.add(new MissionCandidateSnapshot.Candidate(
                    revision.id(), revision.missionId(), revision.revisionNumber(),
                    revision.fallback(), probabilities.get(index)));
        }
        return candidates;
    }

    private static boolean repeatsCluster(MissionRevision revision, List<MissionRevision> previous) {
        return !previous.isEmpty()
                && previous.getLast().semanticCluster().equals(revision.semanticCluster());
    }

    /** 직전부터 거슬러 올라가 같은 목표가 몇 번 연속됐는지 세고, 한도에 닿았는지 본다. */
    private static boolean exceedsGoalRun(MissionRevision revision, List<MissionRevision> previous) {
        int run = 0;
        for (int index = previous.size() - 1; index >= 0; index--) {
            if (previous.get(index).missionGoal() != revision.missionGoal()) {
                break; // 연속이 끊겼다
            }
            run++;
            if (run >= GOAL_RUN_LIMIT) {
                return true;
            }
        }
        return false;
    }

    /** 정책이 허용하는 최대 참여 부담. */
    static boolean withinBurdenLimit(MissionRevision revision) {
        return revision.participationBurden() != MissionAttributeLevel.HIGH;
    }

    static boolean isPreferenceLeakAllowed(MissionRevision revision) {
        return revision.preferenceLeakRisk() != PreferenceLeakRisk.HIGH;
    }

    private record Scored(MissionRevision revision, BigDecimal score) {}

    private record Selection(List<Scored> ranked, MissionAssignmentRelaxationLevel relaxationLevel) {}
}
