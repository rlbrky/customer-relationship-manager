package com.berkay.crm;

import com.berkay.crm.service.csv.CsvWriter;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The security test for the whole milestone. CsvWriter has no dependency on
 * anything in the application, so this needs no Spring context and no database.
 */
public class CsvWriterTest {

    private static final char BOM = '﻿';

    private String write(String[] headers, Object[]... rows) throws IOException {
        StringWriter out = new StringWriter();
        try (CsvWriter csv = new CsvWriter(out, headers)) {
            for (Object[] row : rows) {
                csv.row(row);
            }
        }
        return out.toString();
    }

    /** Re-parses the output instead of string-matching it — see the comma test. */
    private List<CSVRecord> parse(String csv) throws IOException {
        String withoutBom = csv.startsWith(String.valueOf(BOM)) ? csv.substring(1) : csv;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new StringReader(withoutBom))) {
            return parser.getRecords();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"=1+1", "+1", "-1", "@SUM(A1)", "\tx", "\rx"})
    public void escape_neutralisesEveryFormulaPrefix(String hostile) throws IOException {
        // given / when
        List<CSVRecord> records = parse(write(new String[]{"name"}, new Object[]{hostile}));

        // then — the apostrophe makes Excel treat the rest as literal text. Without
        // it, "=cmd|'/c calc'!A0" in an account name is command execution on open.
        assertThat(records.get(0).get("name")).isEqualTo("'" + hostile);
    }

    @Test
    public void escape_leavesOrdinaryTextUntouched() throws IOException {
        // given / when
        List<CSVRecord> records = parse(write(new String[]{"name"}, new Object[]{"Acme Corp"}));

        // then
        assertThat(records.get(0).get("name")).isEqualTo("Acme Corp");
    }

    @Test
    public void row_writesNullAsAnEmptyCell() throws IOException {
        // given / when — not the text "null", which is what toString would produce
        List<CSVRecord> records =
                parse(write(new String[]{"name", "industry"}, new Object[]{"Acme", null}));

        // then
        assertThat(records.get(0).get("industry")).isEmpty();
    }

    @Test
    public void row_roundTripsCommasQuotesAndNewlines() throws IOException {
        // given — the three things a hand-rolled String.join(",") gets wrong
        String awkward = "Acme, \"The\" Corp\nSecond line";

        // when
        List<CSVRecord> records = parse(write(new String[]{"name"}, new Object[]{awkward}));

        // then — asserting by re-parsing, not by matching the raw text: the point is
        // that a reader gets the value back, not that it was quoted a particular way
        assertThat(records).hasSize(1);
        assertThat(records.get(0).get("name")).isEqualTo(awkward);
    }

    @Test
    public void write_startsWithAByteOrderMark() throws IOException {
        // given / when
        String csv = write(new String[]{"name"}, new Object[]{"Acme"});

        // then — without it Excel on Windows reads UTF-8 as the system codepage and
        // every accented character becomes mojibake
        assertThat(csv.charAt(0)).isEqualTo(BOM);
    }

    @Test
    public void write_emitsTheHeaderRow() throws IOException {
        // given / when
        String csv = write(new String[]{"name", "industry"}, new Object[]{"Acme", "Tech"});

        // then
        assertThat(csv.substring(1)).startsWith("name,industry");
    }
}
