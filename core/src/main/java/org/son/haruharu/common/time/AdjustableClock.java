package org.son.haruharu.common.time;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 가상 시각을 고정할 수 있는 테스트용 시계(#180). local·dev 프로필에서만 Clock 빈으로 등록된다.
 * 고정하지 않으면 위임 시계(실시간)를 그대로 따르고, 고정하면 모든 시간 구동 로직(룸 currentDay,
 * 슬롯 시간창, 최종선택 게이트)이 고정 시각을 본다.
 *
 * 고정은 실시간 기준 TTL(4시간)이 지나면 자동 해제된다 — 리셋을 잊어도 다른 테스터의 자연 흐름
 * 테스트와 방 활성화·종료 배치(가드로 skip됨)가 장시간 멈추지 않게 하는 안전장치.
 */
public final class AdjustableClock extends Clock {

    public static final Duration DEFAULT_TTL = Duration.ofHours(4);

    private final Clock delegate;
    private final AtomicReference<Fixation> fixation;

    private record Fixation(Instant fixedInstant, Instant expiresAtReal) {}

    public AdjustableClock(Clock delegate) {
        this(delegate, new AtomicReference<>());
    }

    private AdjustableClock(Clock delegate, AtomicReference<Fixation> fixation) {
        this.delegate = delegate;
        this.fixation = fixation;
    }

    /** 가상 시각을 고정한다. 이전 고정이 있으면 대체된다. */
    public void fix(LocalDateTime virtualNow) {
        Instant fixedInstant = virtualNow.atZone(getZone()).toInstant();
        fixation.set(new Fixation(fixedInstant, delegate.instant().plus(DEFAULT_TTL)));
    }

    /** 실시간으로 복귀한다. */
    public void reset() {
        fixation.set(null);
    }

    /** 유효한(만료되지 않은) 고정이 걸려 있는지. 만료된 고정은 이 호출에서 정리된다. */
    public boolean isFixed() {
        return activeFixation() != null;
    }

    /** 고정된 가상 시각. 고정이 없거나 만료됐으면 empty. */
    public Optional<LocalDateTime> fixedAt() {
        return Optional.ofNullable(activeFixation())
                .map(f -> LocalDateTime.ofInstant(f.fixedInstant(), getZone()));
    }

    /** 고정이 자동 해제되는 실시간 시각. 고정이 없으면 empty. */
    public Optional<LocalDateTime> expiresAt() {
        return Optional.ofNullable(activeFixation())
                .map(f -> LocalDateTime.ofInstant(f.expiresAtReal(), getZone()));
    }

    /** 고정 여부와 무관한 실제 현재 시각. */
    public LocalDateTime realNow() {
        return LocalDateTime.now(delegate);
    }

    @Override
    public Instant instant() {
        Fixation active = activeFixation();
        return active != null ? active.fixedInstant() : delegate.instant();
    }

    @Override
    public ZoneId getZone() {
        return delegate.getZone();
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return new AdjustableClock(delegate.withZone(zone), fixation);
    }

    private Fixation activeFixation() {
        Fixation current = fixation.get();
        if (current == null) {
            return null;
        }
        if (delegate.instant().isAfter(current.expiresAtReal())) {
            fixation.compareAndSet(current, null); // TTL 경과 — 자동 실시간 복귀
            return null;
        }
        return current;
    }
}
