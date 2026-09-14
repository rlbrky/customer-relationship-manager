package com.berkay.crm;

import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.ContactExportService;
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
 * Same isolation rule as AccountExportServiceTest: every fixture owner is a
 * SALES_REP, so visibleTo scopes each export to the rows that test created. The
 * audit and recycle-bin classes commit, and their rows outlive them.
 *
 * Note the extra step here — a contact is not owned directly. Visibility runs
 * contact → account → owner, so scoping a test means giving the ACCOUNT a rep owner.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ContactExportServiceTest {

    @Autowired private ContactExportService contactExportService;
    @Autowired private ContactRepository contactRepository;
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

    private Contact newContact(Account account, String firstName, String lastName) {
        Contact contact = new Contact();
        contact.setAccount(account);
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setEmail(firstName.toLowerCase() + "@example.com");
        contact.setJobTitle("Buyer");
        return contactRepository.save(contact);
    }

    private String export(CrmUser user, String q) throws IOException {
        StringWriter out = new StringWriter();
        contactExportService.writeCsv(out, user, q);
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
        CrmUser owner = newUser("cexp1", Roles.SALES_REP);
        newContact(newAccount(owner, "Acme"), "Ada", "Lovelace");

        // when
        String csv = export(owner, null);

        // then
        assertThat(csv.substring(1))
                .startsWith("firstName,lastName,email,phone,jobTitle,account");
    }

    @Test
    public void writeCsv_namesTheAccountRatherThanItsId() throws IOException {
        // given
        CrmUser owner = newUser("cexp2", Roles.SALES_REP);
        Account account = newAccount(owner, "Acme Corp");
        newContact(account, "Ada", "Lovelace");

        // when
        List<CSVRecord> rows = parse(export(owner, null));

        // then — the decision the whole of 12c rests on. An id is meaningless outside
        // the database that issued it; a name survives a re-seed and can be edited by
        // hand, which is what makes the file importable anywhere.
        assertThat(rows.get(0).get("account")).isEqualTo("Acme Corp");
        assertThat(rows.get(0).get("account")).isNotEqualTo(String.valueOf(account.getId()));
    }

    @Test
    public void writeCsv_includesOnlyVisibleContacts() throws IOException {
        // given — two reps, each with their own account and contact
        CrmUser rep = newUser("cexp3", Roles.SALES_REP);
        CrmUser other = newUser("cexp4", Roles.SALES_REP);
        newContact(newAccount(rep, "Mine"), "Mine", "Contact");
        newContact(newAccount(other, "Theirs"), "Theirs", "Contact");

        // then — visibility reaches through the account, so a rep never sees a contact
        // belonging to someone else's account even though contacts have no owner column
        assertThat(parse(export(rep, null)))
                .extracting(row -> row.get("firstName"))
                .containsExactly("Mine");
    }

    @Test
    public void writeCsv_appliesTheSearchFilter() throws IOException {
        // given
        CrmUser owner = newUser("cexp5", Roles.SALES_REP);
        Account account = newAccount(owner, "Acme");
        newContact(account, "Ada", "Lovelace");
        newContact(account, "Grace", "Hopper");

        // when — the same q the list endpoint takes
        List<CSVRecord> rows = parse(export(owner, "lovelace"));

        // then — one shared specification chain means the file cannot disagree with
        // what the screen showed when the user clicked Export
        assertThat(rows).extracting(row -> row.get("lastName")).containsExactly("Lovelace");
    }

    @Test
    public void writeCsv_excludesSoftDeletedContacts() throws IOException {
        // given
        CrmUser owner = newUser("cexp6", Roles.SALES_REP);
        Account account = newAccount(owner, "Acme");
        newContact(account, "Alive", "Contact");
        contactRepository.delete(newContact(account, "Deleted", "Contact"));

        // when
        List<CSVRecord> rows = parse(export(owner, null));

        // then — ordinary JPA, so @SQLRestriction still covers the export
        assertThat(rows).extracting(row -> row.get("firstName")).containsExactly("Alive");
    }

    @Test
    public void writeCsv_roundTripsAValueContainingACommaAndQuotes() throws IOException {
        // given
        CrmUser owner = newUser("cexp7", Roles.SALES_REP);
        newContact(newAccount(owner, "Acme, \"The\" Corp"), "Ada", "Lovelace");

        // when
        List<CSVRecord> rows = parse(export(owner, null));

        // then — the account name is the field most likely to contain a comma
        assertThat(rows.get(0).get("account")).isEqualTo("Acme, \"The\" Corp");
    }

    @Test
    public void writeCsv_neutralisesAFormulaInAContactName() throws IOException {
        // given — a value that would execute on open in Excel
        CrmUser owner = newUser("cexp8", Roles.SALES_REP);
        newContact(newAccount(owner, "Acme"), "=cmd|'/c calc'!A0", "Lovelace");

        // when
        List<CSVRecord> rows = parse(export(owner, null));

        // then — proves the escaping is wired into THIS export path, not merely unit
        // tested on CsvWriter in isolation
        assertThat(rows.get(0).get("firstName")).startsWith("'=");
    }

    @Test
    public void writeCsv_pagesBeyondASingleBatchWhenEverySortKeyTies() throws IOException {
        // given — 150 contacts against a 100-row batch, ALL sharing a last name so
        // every tie in the sort key straddles the page boundary
        CrmUser owner = newUser("cexp9", Roles.SALES_REP);
        Account account = newAccount(owner, "Acme");
        for (int i = 0; i < 150; i++) {
            newContact(account, String.format("Person %03d", i), "Smith");
        }

        // when
        List<CSVRecord> rows = parse(export(owner, null));

        // then — two failures live here. An off-by-one in hasNext()/nextPageable()
        // truncates the file or loops forever; a non-unique sort key lets MySQL order
        // the ties differently between the two LIMIT/OFFSET queries, so a row is
        // written twice and another vanishes. Both are silent.
        //
        // Ties are necessary for the second failure but not sufficient to force it —
        // MySQL is *permitted* to reorder them, not obliged to. This exercises the
        // path; only "id" in the Sort makes the order total by construction.
        assertThat(rows).hasSize(150);
        assertThat(rows).extracting(row -> row.get("firstName")).doesNotHaveDuplicates();
    }
}
