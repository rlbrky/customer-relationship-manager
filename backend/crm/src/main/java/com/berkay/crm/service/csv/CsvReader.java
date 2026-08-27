package com.berkay.crm.service.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads an uploaded CSV into addressable rows.
 *
 * Two things this exists to get right:
 *
 *  1. The BOM. A file exported by AccountExportService starts with U+FEFF, so its
 *     first header parses as "﻿name" and every get("name") returns null. Our own
 *     export would fail our own import, and it would look like a malformed file
 *     rather than like something we wrote.
 *
 *  2. Charset. Always UTF-8, never the platform default — which on Windows is not
 *     UTF-8, so the same file would parse differently on the developer's machine and
 *     on a Linux server.
 *
 * The whole file is held in memory. That is bounded by the multipart limit (2 MB),
 * which is the deliberate difference from the export: an export has no upper bound
 * and must stream, an upload is capped before it reaches us.
 */
public class CsvReader {

    private static final char BOM = '﻿';

    private final Set<String> headers;

    private final List<CsvRow> rows;

    private CsvReader(Set<String> headers, List<CsvRow> rows) {
        this.headers = headers;
        this.rows = rows;
    }

    /** Header names present in the file, lower-cased and trimmed. */
    public Set<String> headers() {
        return headers;
    }

    /** Every data row, in file order. */
    public List<CsvRow> rows() {
        return rows;
    }

    /**
     * @throws IllegalArgumentException if the file is not parseable CSV — an
     *         unterminated quote, say. That maps to 400 through the global handler,
     *         which is the right answer for a file the user chose.
     */
    public static CsvReader parse(InputStream in) throws IOException {

        Reader reader = skipBom(new InputStreamReader(in, StandardCharsets.UTF_8));

        CSVFormat format = CSVFormat.DEFAULT.builder()
                // no arguments: read the header names FROM the file, so a user who
                // reorders columns still gets a working import
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreHeaderCase(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = format.parse(reader)) {

            Set<String> headers = parser.getHeaderMap().keySet().stream()
                    .map(header -> header.trim().toLowerCase())
                    .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

            List<CsvRow> rows = new ArrayList<>();

            for (CSVRecord record : parser) {
                Map<String, String> values = new LinkedHashMap<>();
                record.toMap().forEach((column, value) ->
                        values.put(column.trim().toLowerCase(), value));

                // +1 because line 1 is the header. Record numbers count data records.
                rows.add(new CsvRow(record.getRecordNumber() + 1, values));
            }

            return new CsvReader(headers, rows);

        } catch (UncheckedIOException | IllegalStateException | IllegalArgumentException ex) {
            // Commons CSV reports a bad quote from the ITERATOR, wrapped in an
            // UncheckedIOException — not from parse(), and not as a plain IOException.
            throw new IllegalArgumentException("That file could not be read as CSV: " + ex.getMessage());
        }
    }

    /**
     * Consumes a leading U+FEFF if present, leaving everything else untouched.
     * PushbackReader lets us look at one character and put it back — the alternative
     * is buffering the whole file to check three bytes.
     */
    private static Reader skipBom(Reader reader) throws IOException {

        PushbackReader pushback = new PushbackReader(reader, 1);
        int first = pushback.read();

        if (first != -1 && first != BOM) {
            pushback.unread(first);
        }

        return pushback;
    }

    /** One data row, addressed by column name rather than position. */
    public record CsvRow(long line, Map<String, String> values) {

        /**
         * Trimmed; an empty cell and an absent column both come back as null, so
         * callers never have to tell "blank" from "missing" per field.
         */
        public String get(String column) {
            String value = values.get(column.trim().toLowerCase());
            return value == null || value.isBlank() ? null : value.trim();
        }
    }
}
