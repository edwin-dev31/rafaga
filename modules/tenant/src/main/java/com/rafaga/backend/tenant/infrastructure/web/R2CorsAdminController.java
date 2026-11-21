package com.rafaga.backend.tenant.infrastructure.web;

import com.rafaga.backend.tenant.application.service.R2CorsSyncService;
import com.rafaga.backend.tenant.domain.model.R2CorsRevisionView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Super-admin API for explicit, versioned Cloudflare R2 CORS origin synchronization and rollback. */
@RestController
@RequestMapping("/api/admin/media/r2-cors")
public class R2CorsAdminController {
    private final R2CorsSyncService syncService;

    public R2CorsAdminController(R2CorsSyncService syncService) {
        this.syncService = syncService;
    }

    @GetMapping("/revisions")
    public List<R2CorsRevisionView> revisions() {
        return syncService.revisions();
    }

    @PostMapping("/sync")
    public R2CorsRevisionView sync(@RequestBody(required = false) SyncR2CorsRequest request) {
        List<String> extra = request == null ? List.of() : request.additionalOrigins();
        return syncService.syncActiveOrigins(extra);
    }

    @PostMapping("/rollback")
    public R2CorsRevisionView rollback() {
        return syncService.rollbackToPrevious();
    }

    public record SyncR2CorsRequest(List<String> additionalOrigins) { }
}
