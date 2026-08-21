package com.berkay.crm.dto;

import java.time.LocalDate;

/**
 * One point on the activity trend. A {@link LocalDate} rather than an Instant:
 * the query has already collapsed absolute moments into calendar days, and the
 * chart plots days.
 */
public record DailyActivity(LocalDate day, long total) {
}
