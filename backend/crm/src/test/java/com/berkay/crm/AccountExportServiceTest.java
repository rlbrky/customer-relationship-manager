package com.berkay.crm;

import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.AccountExportService;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No TestTransaction dance here — unlike the audit tests, nothing in the export
 * depends on a commit, so the default rollback is fine and each test is isolated.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AccountExportServiceTest {

    @Autowired private AccountExportService accountExportService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;

    private CrmUser newUser(String username, String roleName) {
        CrmUser user = new CrmUser();
        user.setUsername(username);
        user.setEmail(username + "@example.com");
        user.setPasswordHash("$2a$10$notARealHashButFillsTheColumn");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setEnabled(true);
        user.getRoles().add(roleRepository.findByName(roleName).orElseThrow());
        return userRepository.save(user);
    }

    private Account newAccount(CrmUser owner, String name) {
        Account account = new Account();
        account.setName(name);
        account.setIndustry("Technology");
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    private String export(CrmUser user, String name, String industry, Long ownerId) throws IOException {
        StringWriter out = new StringWriter();
        accountExportService.writeCsv(out, user, name, industry, ownerId);
        return out.toString();
    }

    /** Re-parses rather than string-matching, so the assertions survive a quoting change. */
    private List<CSVRecord> parse(String csv) throws IOException {
        String withoutBom = csv.startsWith("﻿") ? csv.substring(1) : csv;
        try (CSVParser parser = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build()
                .parse(new StringReader(withoutBom))) {
            return parser.getRecords();
        }
    }

    @Test
    public void writeCsv_emitsAHeaderRow() throws IOException {
        // given
        CrmUser owner = newUser("exp1", Roles.MANAGER);
        newAccount(owner, "Acme");

        // when
        String csv = export(owner, null, null, null);

        // then
        assertThat(csv.substring(1))
                .startsWith("name,industry,website,phone,owner,contactCount,createdDate");
    }

    @Test
    public void writeCsv_includesOnlyVisibleAccounts() throws IOException {
        // given — a rep and somebody else's account
        CrmUser rep = newUser("exp2", Roles.SALES_REP);
        CrmUser other = newUser("exp3", Roles.SALES_REP);
        newAccount(rep, "Mine");
        newAccount(other, "Theirs");

        // when
        List<CSVRecord> rows = parse(export(rep, null, null, null));

        // then — the visibility seed of the shared specification chain, not a
        // separate check the export could have forgotten
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("Mine");
    }

    @Test
    public void writeCsv_appliesTheNameFilter() throws IOException {
        // given
        CrmUser owner = newUser("exp4", Roles.MANAGER);
        newAccount(owner, "Acme Corp");
        newAccount(owner, "Globex");

        // when
        List<CSVRecord> rows = parse(export(owner, "acme", null, null));

        // then — same predicate as the list endpoint, so the file matches the screen
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("Acme Corp");
    }

    @Test
    public void writeCsv_excludesSoftDeletedAccounts() throws IOException {
        // given
        CrmUser owner = newUser("exp5", Roles.MANAGER);
        newAccount(owner, "Alive");
        Account doomed = newAccount(owner, "Deleted");
        accountRepository.delete(doomed);

        // when
        List<CSVRecord> rows = parse(export(owner, null, null, null));

        // then — the export goes through ordinary JPA, so @SQLRestriction still
        // covers it. Only the recycle bin's native queries escape it.
        assertThat(rows).extracting(row -> row.get("name")).containsExactly("Alive");
    }

    @Test
    public void writeCsv_roundTripsANameContainingACommaAndQuotes() throws IOException {
        // given
        CrmUser owner = newUser("exp6", Roles.MANAGER);
        newAccount(owner, "Acme, \"The\" Corp");

        // when
        List<CSVRecord> rows = parse(export(owner, null, null, null));

        // then
        assertThat(rows.get(0).get("name")).isEqualTo("Acme, \"The\" Corp");
    }

    @Test
    public void writeCsv_neutralisesAFormulaInAnAccountName() throws IOException {
        // given — a name that would execute on open in Excel
        CrmUser owner = newUser("exp7", Roles.MANAGER);
        newAccount(owner, "=cmd|'/c calc'!A0");

        // when
        List<CSVRecord> rows = parse(export(owner, null, null, null));

        // then — the escaping is not merely unit-tested in isolation, it is actually
        // wired into the export path
        assertThat(rows.get(0).get("name")).startsWith("'=");
    }

    @Test
    public void writeCsv_pagesBeyondASingleBatch() throws IOException {
        // given — more accounts than the service's 200-row batch, so the loop has to
        // run at least twice
        CrmUser owner = newUser("exp8", Roles.MANAGER);
        for (int i = 0; i < 250; i++) {
            newAccount(owner, String.format("Account %03d", i));
        }

        // when
        List<CSVRecord> rows = parse(export(owner, null, null, null));

        // then — an off-by-one in hasNext()/nextPageable() either truncates the file
        // at 200 rows or loops forever duplicating them. Both are silent data bugs
        // no other test in this class would catch.
        assertThat(rows).hasSize(250);
        assertThat(rows).extracting(row -> row.get("name")).doesNotHaveDuplicates();
    }
}
