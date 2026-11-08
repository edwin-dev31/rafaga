package com.rafaga.backend.inventory.application.service;

import com.rafaga.backend.catalog.application.service.ProductService;
import com.rafaga.backend.catalog.application.service.ProductView;
import com.rafaga.backend.common.application.tenant.TenantContext;
import com.rafaga.backend.inventory.application.exception.StockNotFoundException;
import com.rafaga.backend.inventory.application.port.StockStore;
import com.rafaga.backend.inventory.domain.event.StockAdjustedEvent;
import com.rafaga.backend.inventory.domain.event.StockLowEvent;
import com.rafaga.backend.inventory.domain.event.StockReleasedEvent;
import com.rafaga.backend.inventory.domain.event.StockReservedEvent;
import com.rafaga.backend.inventory.domain.model.InventoryMovement;
import com.rafaga.backend.inventory.domain.model.MovementType;
import com.rafaga.backend.inventory.domain.model.StockLevel;
import com.rafaga.backend.inventory.domain.model.StockReservation;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockService {
    private final StockStore store;
    private final ProductService products;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Autowired
    public StockService(StockStore store, ProductService products, ApplicationEventPublisher events) {
        this(store, products, events, Clock.systemUTC());
    }

    StockService(StockStore store, ProductService products, ApplicationEventPublisher events, Clock clock) {
        this.store = store;
        this.products = products;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public StockLevelView getStockLevel(UUID productId, UUID variantId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return StockLevelView.from(store.findLevel(tenantId, productId, variantId)
                .orElseThrow(() -> new StockNotFoundException("Stock level not found")));
    }

    @Transactional
    public void initializeProduct(UUID productId) {
        String tenantId = TenantContext.getCurrentTenantId();
        ProductView product = products.getProductById(productId);
        initializeLevel(tenantId, productId, null);
        for (ProductView.VariantView variant : product.variants()) {
            initializeLevel(tenantId, productId, variant.id());
        }
    }

    @Transactional
    public StockLevelView adjustStock(UUID productId, UUID variantId, int delta, String notes) {
        validateProductVariant(productId, variantId);
        String tenantId = TenantContext.getCurrentTenantId();
        StockLevel level = store.lockLevel(tenantId, productId, variantId)
                .orElseGet(() -> new StockLevel(tenantId, productId, variantId, clock.instant()));
        int previous = level.getTotalQuantity();
        level.adjust(delta, clock.instant());
        store.saveLevel(level);
        movement(level, MovementType.ADJUSTMENT, Math.abs(delta), previous, null, notes);
        events.publishEvent(new StockAdjustedEvent(productId, variantId, tenantId,
                previous, level.getTotalQuantity(), MovementType.ADJUSTMENT));
        publishLow(level);
        return StockLevelView.from(level);
    }

    @Transactional
    public ReservationView reserveStock(UUID productId, UUID variantId, int quantity,
            String referenceId, Instant expiresAt) {
        validateProductVariant(productId, variantId);
        if (quantity <= 0) throw new IllegalArgumentException("Reservation quantity must be positive");
        if (referenceId == null || referenceId.isBlank() || referenceId.length() > 100) {
            throw new IllegalArgumentException("Invalid reservation reference");
        }
        String tenantId = TenantContext.getCurrentTenantId();
        StockLevel level = lockedLevel(tenantId, productId, variantId);
        for (StockReservation existing : store.findActiveByReference(tenantId, referenceId)) {
            if (existing.getProductId().equals(productId)
                    && Objects.equals(existing.getVariantId(), variantId)) {
                if (existing.getQuantity() != quantity) {
                    throw new IllegalArgumentException("Reference already reserved with another quantity");
                }
                return ReservationView.from(existing);
            }
        }
        int previous = level.getTotalQuantity();
        level.reserve(quantity, clock.instant());
        store.saveLevel(level);
        StockReservation reservation = store.saveReservation(new StockReservation(
                tenantId, productId, variantId, quantity, referenceId, clock.instant(), expiresAt));
        movement(level, MovementType.RESERVATION, quantity, previous, referenceId, null);
        events.publishEvent(new StockReservedEvent(reservation.getId(), productId, variantId,
                tenantId, quantity));
        publishLow(level);
        return ReservationView.from(reservation);
    }

    @Transactional
    public ReservationView releaseReservation(UUID reservationId) {
        return release(reservationId, false);
    }

    @Transactional
    public ReservationView expireReservation(UUID reservationId) {
        return release(reservationId, true);
    }

    @Transactional
    public ReservationView confirmReservation(UUID reservationId) {
        String tenantId = TenantContext.getCurrentTenantId();
        StockReservation reservation = activeReservation(tenantId, reservationId);
        StockLevel level = lockedLevel(tenantId, reservation.getProductId(), reservation.getVariantId());
        int previous = level.getTotalQuantity();
        level.confirm(reservation.getQuantity(), clock.instant());
        reservation.confirm();
        store.saveLevel(level);
        store.saveReservation(reservation);
        movement(level, MovementType.SALE, reservation.getQuantity(), previous,
                reservation.getReferenceId(), null);
        events.publishEvent(new StockAdjustedEvent(reservation.getProductId(),
                reservation.getVariantId(), tenantId, previous, level.getTotalQuantity(), MovementType.SALE));
        publishLow(level);
        return ReservationView.from(reservation);
    }

    @Transactional(readOnly = true)
    public List<MovementView> getMovements(UUID productId, UUID variantId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return store.findMovements(tenantId, productId, variantId).stream()
                .map(MovementView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationView> getActiveReservations(String referenceId) {
        String tenantId = TenantContext.getCurrentTenantId();
        return store.findActiveByReference(tenantId, referenceId).stream()
                .map(ReservationView::from).toList();
    }

    @Transactional(readOnly = true)
    public List<ReservationView> getExpiredReservations(Instant now) {
        String tenantId = TenantContext.getCurrentTenantId();
        return store.findExpired(tenantId, now).stream().map(ReservationView::from).toList();
    }

    private ReservationView release(UUID reservationId, boolean expired) {
        String tenantId = TenantContext.getCurrentTenantId();
        StockReservation reservation = activeReservation(tenantId, reservationId);
        if (expired && (reservation.getExpiresAt() == null
                || reservation.getExpiresAt().isAfter(clock.instant()))) {
            throw new IllegalStateException("Reservation has not expired");
        }
        StockLevel level = lockedLevel(tenantId, reservation.getProductId(), reservation.getVariantId());
        int previous = level.getTotalQuantity();
        level.release(reservation.getQuantity(), clock.instant());
        if (expired) reservation.expire(); else reservation.release();
        store.saveLevel(level);
        store.saveReservation(reservation);
        movement(level, MovementType.RELEASE, reservation.getQuantity(), previous,
                reservation.getReferenceId(), null);
        events.publishEvent(new StockReleasedEvent(reservationId, reservation.getProductId(),
                reservation.getVariantId(), tenantId, reservation.getQuantity()));
        return ReservationView.from(reservation);
    }

    private StockReservation activeReservation(String tenantId, UUID reservationId) {
        StockReservation reservation = store.lockReservation(tenantId, reservationId)
                .orElseThrow(() -> new StockNotFoundException("Reservation not found"));
        if (reservation.getStatus() != com.rafaga.backend.inventory.domain.model.ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Reservation is not active");
        }
        return reservation;
    }

    private StockLevel lockedLevel(String tenantId, UUID productId, UUID variantId) {
        return store.lockLevel(tenantId, productId, variantId)
                .orElseThrow(() -> new StockNotFoundException("Stock level not found"));
    }

    private void initializeLevel(String tenantId, UUID productId, UUID variantId) {
        if (store.findLevel(tenantId, productId, variantId).isEmpty()) {
            store.saveLevel(new StockLevel(tenantId, productId, variantId, clock.instant()));
        }
    }

    private void validateProductVariant(UUID productId, UUID variantId) {
        ProductView product = products.getProductById(productId);
        if (variantId != null && product.variants().stream().noneMatch(v -> variantId.equals(v.id()))) {
            throw new StockNotFoundException("Product variant not found");
        }
    }

    private void movement(StockLevel level, MovementType type, int quantity,
            int previous, String referenceId, String notes) {
        store.saveMovement(new InventoryMovement(level.getTenantId(), level.getProductId(),
                level.getVariantId(), type, quantity, previous, level.getTotalQuantity(),
                referenceId, notes, clock.instant()));
    }

    private void publishLow(StockLevel level) {
        if (level.getAvailableQuantity() <= level.getReorderLevel()) {
            events.publishEvent(new StockLowEvent(level.getProductId(), level.getVariantId(),
                    level.getTenantId(), level.getAvailableQuantity(), level.getReorderLevel()));
        }
    }
}
