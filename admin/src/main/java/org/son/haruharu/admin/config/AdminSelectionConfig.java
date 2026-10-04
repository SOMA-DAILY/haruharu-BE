package org.son.haruharu.admin.config;

import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.storage.FinalSelectionRepositoryImpl;
import org.son.haruharu.domain.room.storage.MatchResultRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({FinalSelectionReader.class, MatchResultReader.class, FinalSelectionRepositoryImpl.class, MatchResultRepositoryImpl.class})
public class AdminSelectionConfig {}
