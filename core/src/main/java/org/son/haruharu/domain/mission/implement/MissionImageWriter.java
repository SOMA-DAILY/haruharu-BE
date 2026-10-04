package org.son.haruharu.domain.mission.implement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class MissionImageWriter { private final MissionImageRepository repository; public MissionImage save(MissionImage image) { return repository.save(image); } }
