package com.berkay.crm.dto;

import java.time.Instant;
import java.util.List;

public record RevisionResponse(
        long revision,
        Instant changedAt,
        String changedBy,
        String type, // ADD , MOD, DEL
        List<FieldChange> changes
) {
}
