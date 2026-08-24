package com.berkay.crm.service.csv;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Set;

/**
 * Writes CSV that Excel can open without either mangling it or executing it.
 *
 * Two problems this exists to solve, neither of which a CSV library solves for you:
 *
 *  1. Formula injection. Excel evaluates a cell beginning '=', '+', '-' or '@' as a
 *     formula, so an account named {@code =cmd|'/c calc'!A0} is a command-execution
 *     vector the moment someone opens the file. Every value goes through escape().
 *
 *  2. Encoding. Excel on Windows reads a UTF-8 file as the system codepage unless it
 *     starts with a byte-order mark, turning any accented character into mojibake.
 *
 * Quoting, embedded commas and embedded newlines ARE the library's job — that is the
 * reason not to hand-roll String.join(",").
 */
public class CsvWriter implements AutoCloseable {

    /**
     * The characters Excel and LibreOffice treat as "a formula follows".
     * Tab and carriage return are here because they can smuggle one past a naive
     * check on the first three.
     */
    private static final Set<Character> FORMULA_PREFIXES = Set.of('=', '+', '-', '@', '\t', '\r');

    /** U+FEFF. Excel needs it; write it once, before anything else. */
    private static final char BOM = '﻿';

    private final CSVPrinter printer;

    public CsvWriter(Writer writer, String... headers) throws IOException {
        writer.write(BOM);
        this.printer = CSVFormat.DEFAULT.builder()
                .setHeader(headers)
                .build()
                .print(writer);
    }

    /** Writes one record. Nulls become empty cells, not the text "null". */
    public void row(Object... values) throws IOException {
        printer.printRecord(Arrays.stream(values).map(CsvWriter::escape).toList());
    }

    @Override
    public void close() throws IOException {
        printer.flush();
    }

    /**
     * Neutralises a leading formula character by prefixing an apostrophe, which Excel
     * reads as "treat the rest as literal text" and does not display.
     *
     * Note the cost: a genuinely negative number exports as '-42. Acceptable here
     * because every exported column is text or a non-negative count — but it is the
     * reason this belongs in one reviewable place rather than scattered per column.
     */
    static String escape(Object value) {
        if (value == null) {
            return "";
        }

        String text = value.toString();
        if (text.isEmpty()) {
            return text;
        }

        return FORMULA_PREFIXES.contains(text.charAt(0)) ? "'" + text : text;
    }
}
