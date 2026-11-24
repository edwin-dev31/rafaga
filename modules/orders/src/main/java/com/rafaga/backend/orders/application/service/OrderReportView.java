package com.rafaga.backend.orders.application.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Aggregated order metrics over a date range, grouped by order status.
 * Explicitly separated from payment collection concepts.
 */
public record OrderReportView(
        Instant from,
        Instant to,
        long totalOrders,
        BigDecimal grossOrderAmount,
        BigDecimal averageOrderValue,
        List<StatusBreakdown> byStatus,
        String paymentDisclaimer) {

    public static final String PAYMENT_DISCLAIMER = "ORDER_TOTALS_DO_NOT_REPRESENT_COLLECTED_PAYMENTS";

    public record StatusBreakdown(String status, long orderCount, BigDecimal totalAmount) { }
}
