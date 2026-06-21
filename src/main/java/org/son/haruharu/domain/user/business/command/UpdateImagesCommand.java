package org.son.haruharu.domain.user.business.command;

import java.util.List;

public record UpdateImagesCommand(List<String> imageUrls) {}
