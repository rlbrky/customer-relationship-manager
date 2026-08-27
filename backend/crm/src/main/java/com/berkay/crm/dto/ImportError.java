package com.berkay.crm.dto;

/**
 * One thing wrong with one row.
 *
 * {@code line} is the 1-based line number IN THE FILE, counting the header — what
 * Excel shows in the corner. An index into a parsed list is off by at least one and
 * useless to whoever has to fix the file.
 */
public record ImportError(long line, String column, String message) {
}
