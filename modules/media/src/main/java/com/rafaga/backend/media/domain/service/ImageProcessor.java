package com.rafaga.backend.media.domain.service;

import java.io.InputStream;

public interface ImageProcessor {
    ProcessingResult process(InputStream imageStream, String declaredContentType);
}
