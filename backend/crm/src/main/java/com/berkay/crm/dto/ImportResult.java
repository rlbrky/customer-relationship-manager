package com.berkay.crm.dto;

import java.util.List;

/**
 * The outcome of one upload.
 *
 * Returned with 200 even when nothing imported: a complete analysis of a bad file is
 * a successful request, and clients habitually discard the body on a 4xx — which is
 * exactly where all the value lives.
 *
 * imported is either 0 or totalRows, never in between. The import validates every
 * row first and only writes if all of them passed.
 */
public record ImportResult(int totalRows, int imported, List<ImportError> errors) {
}
