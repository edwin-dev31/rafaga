package com.rafaga.backend.media.application.exception;

import java.util.UUID;

public class MediaNotFoundException extends RuntimeException {
    public MediaNotFoundException(String entity, UUID id) {
        super(entity + " not found: " + id);
    }
}
