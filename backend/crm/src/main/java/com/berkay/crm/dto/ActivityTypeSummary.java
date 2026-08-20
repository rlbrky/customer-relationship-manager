package com.berkay.crm.dto;

import com.berkay.crm.model.ActivityType;

public record ActivityTypeSummary(ActivityType type, long total) {
}
