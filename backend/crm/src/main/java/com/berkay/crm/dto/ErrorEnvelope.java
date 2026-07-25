package com.berkay.crm.dto;

import java.time.Instant;

public record ErrorEnvelope(Instant timestamp, int status, String error, String message, String path) {

}
