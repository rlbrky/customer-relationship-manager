package com.berkay.crm;

import com.berkay.crm.service.csv.CsvReader;
import com.berkay.crm.service.csv.CsvWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** No Spring, no database — CsvReader depends on nothing in the application. */
public class CsvReaderTest {

    private CsvReader parse(String... lines) throws IOException {
        String csv = String.join("\n", lines);
        return CsvReader.parse(new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    public void parse_addressesCellsByColumnName() throws IOException {
        // given / when
        CsvReader reader = parse("name,industry", "Acme,Technology");

        // then
        assertThat(reader.rows()).hasSize(1);
        assertThat(reader.rows().get(0).get("name")).isEqualTo("Acme");
        assertThat(reader.rows().get(0).get("industry")).isEqualTo("Technology");
    }

    @Test
    public void parse_numbersTheFirstDataRowAsLineTwo() throws IOException {
        // given / when — line 1 is the header
        CsvReader reader = parse("name", "Acme", "Globex");

        // then — this is what an error message points the user at, so an off-by-one
        // here sends them to the wrong row in Excel
        assertThat(reader.rows().get(0).line()).isEqualTo(2);
        assertThat(reader.rows().get(1).line()).isEqualTo(3);
    }

    @Test
    public void parse_stripsAByteOrderMark() throws IOException {
        // given — exactly what AccountExportService writes
        CsvReader reader = parse("﻿name,industry", "Acme,Technology");

        // then — without the strip the first header is "﻿name", get("name")
        // returns null for every row, and our own export fails our own import
        assertThat(reader.headers()).contains("name");
        assertThat(reader.rows().get(0).get("name")).isEqualTo("Acme");
    }

    @Test
    public void parse_treatsAnEmptyCellAsNull() throws IOException {
        // given / when
        CsvReader reader = parse("name,industry", "Acme,");

        // then — not "", so callers never distinguish blank from missing
        assertThat(reader.rows().get(0).get("industry")).isNull();
    }

    @Test
    public void parse_treatsAnAbsentColumnAsNull() throws IOException {
        // given / when
        CsvReader reader = parse("name", "Acme");

        // then — Commons CSV would throw IllegalArgumentException on an unknown
        // column; swallowing it here is what lets the service treat "optional
        // column not supplied" and "cell left blank" identically
        assertThat(reader.rows().get(0).get("phone")).isNull();
    }

    @Test
    public void parse_isCaseInsensitiveAboutHeaders() throws IOException {
        // given — Excel and hand-edited files disagree about capitalisation
        CsvReader reader = parse("Name,INDUSTRY", "Acme,Technology");

        // then
        assertThat(reader.headers()).contains("name", "industry");
        assertThat(reader.rows().get(0).get("name")).isEqualTo("Acme");
    }

    @Test
    public void parse_readsQuotedCommasAndNewlinesAsOneCell() throws IOException {
        // given — the three things a hand-rolled split(",") gets wrong
        CsvReader reader = parse("name,industry", "\"Acme, \"\"The\"\" Corp\nSecond line\",Technology");

        // then
        assertThat(reader.rows()).hasSize(1);
        assertThat(reader.rows().get(0).get("name")).isEqualTo("Acme, \"The\" Corp\nSecond line");
        assertThat(reader.rows().get(0).get("industry")).isEqualTo("Technology");
    }

    @Test
    public void parse_decodesUtf8RegardlessOfPlatformDefault() throws IOException {
        // given / when
        CsvReader reader = parse("name", "Şirket Ünïcode");

        // then — reading with the platform charset would mangle this on Windows
        assertThat(reader.rows().get(0).get("name")).isEqualTo("Şirket Ünïcode");
    }

    @Test
    public void parse_reportsHeadersEvenWhenThereAreNoRows() throws IOException {
        // given / when — a header-only file is valid, just empty
        CsvReader reader = parse("name,industry");

        // then
        assertThat(reader.headers()).contains("name", "industry");
        assertThat(reader.rows()).isEmpty();
    }

    @Test
    public void parse_rejectsAnUnterminatedQuote() {
        // given / when / then — IllegalArgumentException maps to 400 through the
        // global handler, which is right for a file the user chose
        assertThatThrownBy(() -> parse("name", "\"never closed"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── round trip: what CsvWriter writes, CsvReader must read back unchanged ──

    /** One value, written by the real CsvWriter and read back by the real CsvReader. */
    private String roundTrip(String value) throws IOException {
        StringWriter out = new StringWriter();
        try (CsvWriter csv = new CsvWriter(out, "name")) {
            csv.row(value);
        }

        CsvReader reader = CsvReader.parse(
                new ByteArrayInputStream(out.toString().getBytes(StandardCharsets.UTF_8)));
        return reader.rows().get(0).get("name");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "+90 212 555 0100",   // the case that started this: every international phone number
            "=1+1", "-1", "@SUM(A1)",
            "'=1+1",              // a real apostrophe before a formula character
            "''",                 // nothing but escape characters
            "'Twas Brillig Ltd",  // a real apostrophe before ordinary text
            "O'Brien",            // an apostrophe that is not leading at all
            "Acme Corp"})
    public void roundTrip_returnsExactlyWhatWasWritten(String value) throws IOException {
        // given / when / then — "'=1+1" and "''" are the two that fail if escape()
        // does not also escape the apostrophe: without that, "=1+1" and "'=1+1" are
        // written identically, and no reader can recover which one it was
        assertThat(roundTrip(value)).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\tx", "\rx"})
    public void roundTrip_stillTrimsAWhitespacePrefix(String value) throws IOException {
        // given / when / then — the import has always trimmed (M12b). The apostrophe
        // must not smuggle a leading tab past that, which is why get() unescapes first.
        assertThat(roundTrip(value)).isEqualTo("x");
    }

    @ParameterizedTest
    @ValueSource(strings = {"\t", "\r", " "})
    public void roundTrip_returnsABlankValueAsNull(String value) throws IOException {
        // given / when / then — escape("\t") writes '\t, a blank value in disguise. It
        // must come back null like any empty cell, not as a lone apostrophe — which
        // would sail through @NotBlank and become somebody's first name.
        assertThat(roundTrip(value)).isNull();
    }

    @Test
    public void parse_keepsALeadingApostropheEscapeCouldNotHaveWritten() throws IOException {
        // given — a hand-written file, not one of our exports. escape() never writes an
        // apostrophe followed by ordinary text, so this one is data, not an escape.
        CsvReader reader = parse("name", "'Twas Brillig Ltd");

        // then
        assertThat(reader.rows().get(0).get("name")).isEqualTo("'Twas Brillig Ltd");
    }
}
