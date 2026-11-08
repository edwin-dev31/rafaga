package com.rafaga.backend.catalog.application.service;

import java.util.List;
import java.util.UUID;

public record CategoryNode(UUID id, String name, String slug, int displayOrder,
        List<CategoryNode> children) {
}
