package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.service.TenantDirectoryService;
import com.rafaga.backend.tenant.domain.model.TenantDirectorySnapshot;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Internal-only contract polled by Traefik; it exposes no credentials or feature data. */
@RestController
@RequestMapping("/api/internal/tenant-directory")
public class TenantDirectoryController {
    private final TenantDirectoryService directory;
    private final String token;

    public TenantDirectoryController(
            TenantDirectoryService directory,
            @Value("${rafaga.tenant-directory.internal-token:}") String token) {
        this.directory = directory;
        this.token = token;
    }

    @GetMapping
    public TenantDirectorySnapshot snapshot(
            @RequestParam(name = "afterVersion", defaultValue = "-1") long afterVersion,
            @RequestHeader(value = "X-Tenant-Directory-Token", required = false) String supplied) {
        if (token.isBlank()
                || supplied == null
                || !MessageDigest.isEqual(
                        token.getBytes(StandardCharsets.UTF_8),
                        supplied.getBytes(StandardCharsets.UTF_8))) {
            throw new DirectoryAccessDeniedException();
        }
        return directory.snapshot(afterVersion);
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    static class DirectoryAccessDeniedException extends RuntimeException { }
}
