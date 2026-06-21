package org.son.haruharu.domain.profile.business.command;

import java.util.List;

public record UpdateImagesCommand(List<String> imageUrls) {}
