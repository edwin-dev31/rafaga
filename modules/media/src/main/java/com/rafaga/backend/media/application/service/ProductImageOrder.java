package com.rafaga.backend.media.application.service;

import java.util.UUID;

public record ProductImageOrder(UUID imageId, int position, boolean isPrimary) {
}
