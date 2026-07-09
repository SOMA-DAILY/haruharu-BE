package org.son.haruharu.domain.meta.business;

import org.son.haruharu.domain.meta.business.dto.EnumOptionResponse;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 선택지형 enum을 code+label 목록으로 조립한다. 참조 데이터라 트랜잭션·저장소 접근이 없다. */
@Service
public class MetaEnumService {

    public Map<String, List<EnumOptionResponse>> getEnumOptions() {
        Map<String, List<EnumOptionResponse>> options = new LinkedHashMap<>();
        for (EnumCatalog catalog : EnumCatalog.values()) {
            options.put(catalog.key(), Arrays.stream(catalog.options())
                    .map(EnumOptionResponse::from)
                    .toList());
        }
        return options;
    }
}
