package com.berkay.crm.dto;

import java.time.Instant;

public record DeletedAccountResponse(
        Long id, String name, String industry,
        Instant deletedAt, String ownerName, String deletedBy) {
}
