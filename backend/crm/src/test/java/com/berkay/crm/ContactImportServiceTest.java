package com.berkay.crm;

import com.berkay.crm.dto.ImportError;
import com.berkay.crm.dto.ImportResult;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.repository.specification.AccountSpecifications;
import com.berkay.crm.repository.specification.ContactSpecifications;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.ContactExportService;
import com.berkay.crm.service.ContactImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same isolation rule as the other CSV tests: fixture owners are SALES_REPs, so
 * visibleTo scopes both the account resolution and the assertions to what each test
 * created. The audit and recycle-bin classes commit, and their rows outlive them.
 *
 * The one place that rule cannot help is a MANAGER test — visibleTo is a no-op for a
 * manager, so those use account names no leftover row could share.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ContactImportServiceTest {

    /** Exactly the header ContactExportService writes. */
    private static final String HEADER = "firstName,lastName,email,phone,jobTitle,account";

    @Autowired private ContactImportService contactImportService;
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
        account.setOwner(owner);
        return accountRepository.save(account);
    }

    private Contact newContact(Account account, String firstName, String lastName) {
        Contact contact = new Contact();
        contact.setAccount(account);
        contact.setFirstName(firstName);
        contact.setLastName(lastName);
        contact.setEmail(firstName.toLowerCase() + "@example.com");
        return contactRepository.save(contact);
    }

    private ImportResult importCsv(CrmUser user, String... lines) throws IOException {
        String csv = String.join("\n", lines);
        return contactImportService.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), user);
    }

    /** Only the contacts this caller can see — never a global count. */
    private List<Contact> contactsOf(CrmUser user) {
        return contactRepository.findAll(ContactSpecifications.visibleTo(user));
    }

    @Test
    public void import_createsEveryValidRow() throws IOException {

        // given
        CrmUser rep = newUser("cimp1", Roles.SALES_REP);
        Account acme = newAccount(rep, "Acme");

        // when
        ImportResult result = importCsv(rep, HEADER,
                "Ada,Lovelace,ada@example.com,555-0100,CTO,Acme",
                "Grace,Hopper,,,,Acme");

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.imported()).isEqualTo(2);
        assertThat(contactsOf(rep))
                .allSatisfy(contact -> assertThat(contact.getAccount().getId()).isEqualTo(acme.getId()))
                .extracting(Contact::getLastName)
                .containsExactlyInAnyOrder("Lovelace", "Hopper");
    }

    @Test
    public void import_acceptsAFileFromOurOwnExport() throws IOException {

        // given — two contacts, exported by the real export service
        CrmUser rep = newUser("cimp2", Roles.SALES_REP);
        Account acme = newAccount(rep, "Acme");
        newContact(acme, "Ada", "Lovelace");
        newContact(acme, "Grace", "Hopper");

        StringWriter exported = new StringWriter();
        contactExportService.writeCsv(exported, rep, null);

        // when — fed straight back in, BOM and all
        ImportResult result = contactImportService.importCsv(
                new ByteArrayInputStream(exported.toString().getBytes(StandardCharsets.UTF_8)), rep);

        // then — the round trip is the whole reason the export names the account
        // instead of writing its id. No header or column should need editing.
        assertThat(result.errors()).isEmpty();
        assertThat(result.imported()).isEqualTo(2);
        assertThat(contactsOf(rep)).hasSize(4);
    }

    @Test
    public void import_matchesTheAccountNameCaseInsensitively() throws IOException {

        // given
        CrmUser rep = newUser("cimp3", Roles.SALES_REP);
        Account acme = newAccount(rep, "Acme Corp");

        // when
        ImportResult result = importCsv(rep, HEADER, "Ada,Lovelace,,,,ACME CORP");

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(contactsOf(rep).get(0).getAccount().getId()).isEqualTo(acme.getId());
    }

    @Test
    public void import_reportsAnAccountThatDoesNotExist() throws IOException {

        // given
        CrmUser rep = newUser("cimp4", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(rep, HEADER, "Ada,Lovelace,,,,Nowhere Ltd");

        // then
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("account");
        assertThat(result.errors().get(0).message()).contains("Nowhere Ltd");
    }

    @Test
    public void import_givesTheSameAnswerForAnAccountTheUserCannotSee() throws IOException {

        // given — "Theirs Ltd" exists, but belongs to another rep
        CrmUser rep = newUser("cimp5", Roles.SALES_REP);
        CrmUser other = newUser("cimp6", Roles.SALES_REP);
        newAccount(other, "Theirs Ltd");

        // when — one name that exists but is invisible, one that exists nowhere
        ImportError hidden = importCsv(rep, HEADER, "Ada,Lovelace,,,,Theirs Ltd").errors().get(0);
        ImportError unknown = importCsv(rep, HEADER, "Ada,Lovelace,,,,Nowhere Ltd").errors().get(0);

        // then — both must be ACCOUNT errors first. Without this, any failure that
        // produced the same message for both files (a rejected header, say) would pass
        // the comparison below while never reaching the lookup at all.
        assertThat(hidden.column()).isEqualTo("account");
        assertThat(unknown.column()).isEqualTo("account");

        // ... and identical apart from the name the user typed. If these two messages
        // differed at all, a rep could upload one row per guess and enumerate every
        // account name in the company from the difference.
        assertThat(hidden.message().replace("Theirs Ltd", "<name>"))
                .isEqualTo(unknown.message().replace("Nowhere Ltd", "<name>"));

        // ... and of course nothing was attached to the other rep's account
        assertThat(contactsOf(other)).isEmpty();
    }

    @Test
    public void import_reportsAnAmbiguousAccountName() throws IOException {

        // given — account.name has no unique constraint, so this is legal data
        CrmUser rep = newUser("cimp7", Roles.SALES_REP);
        newAccount(rep, "Twin");
        newAccount(rep, "Twin");

        // when
        ImportResult result = importCsv(rep, HEADER, "Ada,Lovelace,,,,Twin");

        // then — refusing to guess. Picking either one would attach the contact to an
        // account the user may not have meant, with nothing on screen to say so.
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("account");
        assertThat(result.imported()).isZero();
    }

    @Test
    public void import_decidesNameEqualityInJavaNotInTheDatabaseCollation() throws IOException {

        // given — MySQL's default collation (utf8mb4_0900_ai_ci) is accent-INsensitive,
        // so the resolution query returns BOTH of these accounts for "cafe"
        CrmUser rep = newUser("cimp17", Roles.SALES_REP);
        Account plain = newAccount(rep, "Cafe");
        newAccount(rep, "Café");

        // the premise, checked rather than assumed: if the collation ever becomes
        // accent-sensitive, this fails and says the test no longer tests anything
        assertThat(accountRepository.findAll(AccountSpecifications.visibleTo(rep)
                .and(AccountSpecifications.nameIn(Set.of("cafe"))))).hasSize(2);

        // when
        ImportResult result = importCsv(rep, HEADER, "Ada,Lovelace,,,,Cafe");

        // then — resolved to the exact name, not reported as ambiguous. The SQL is only
        // a prefilter; groupingBy in Java makes the actual decision. Because the
        // collation is looser than Java's comparison, the difference can only ever
        // produce a miss — never an attachment to an account the file did not name.
        assertThat(result.errors()).isEmpty();
        assertThat(contactsOf(rep)).extracting(contact -> contact.getAccount().getId())
                .containsExactly(plain.getId());
    }

    @Test
    public void import_letsAManagerTargetAnyAccount() throws IOException {

        // given — a rep's account, uploaded against by a manager. The name is unique
        // to this test because visibleTo does not scope a manager.
        CrmUser manager = newUser("cimp8", Roles.MANAGER);
        CrmUser rep = newUser("cimp9", Roles.SALES_REP);
        Account target = newAccount(rep, "cimp9 Target Ltd");

        // when
        ImportResult result = importCsv(manager, HEADER, "Ada,Lovelace,,,,cimp9 Target Ltd");

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(contactsOf(rep)).extracting(contact -> contact.getAccount().getId())
                .containsExactly(target.getId());
    }

    @Test
    public void import_requiresAnAccountOnEveryRow() throws IOException {

        // given — the column exists, this row's cell is blank
        CrmUser rep = newUser("cimp10", Roles.SALES_REP);
        newAccount(rep, "Acme");

        // when
        ImportResult result = importCsv(rep, HEADER, "Ada,Lovelace,,,,");

        // then — account is not a field on ContactCreateRequest, so Bean Validation
        // cannot catch this one; it needs its own check
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("account");
    }

    @Test
    public void import_doesNotResolveASoftDeletedAccount() throws IOException {

        // given
        CrmUser rep = newUser("cimp11", Roles.SALES_REP);
        accountRepository.delete(newAccount(rep, "Gone Ltd"));
        accountRepository.flush();

        // when
        ImportResult result = importCsv(rep, HEADER, "Ada,Lovelace,,,,Gone Ltd");

        // then — nothing in the import checks deleted_at. The resolution query runs
        // through ordinary JPA, so @SQLRestriction on Account does it for free.
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("account");
    }

    @Test
    public void import_reportsEveryRowThatNamesABadAccount() throws IOException {

        // given — one unknown name, used on two rows
        CrmUser rep = newUser("cimp12", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(rep, HEADER,
                "Ada,Lovelace,,,,Nowhere Ltd",
                "Grace,Hopper,,,,Nowhere Ltd");

        // then — the name is resolved ONCE, but each row genuinely fails and each
        // needs fixing, so each gets its own line in the report
        assertThat(result.errors()).extracting(ImportError::line).containsExactly(2L, 3L);
    }

    @Test
    public void import_writesNothingWhenAnyRowFails() throws IOException {

        // given — one good row, one bad
        CrmUser rep = newUser("cimp13", Roles.SALES_REP);
        newAccount(rep, "Acme");

        // when
        ImportResult result = importCsv(rep, HEADER,
                "Ada,Lovelace,,,,Acme",
                "Grace,Hopper,,,,Nowhere Ltd");

        // then — the failure is the one we planted, on line 3. Otherwise a file
        // rejected wholesale at the header would pass this test without testing it.
        assertThat(result.errors()).extracting(ImportError::line).containsExactly(3L);

        // ... and line 2, which was perfectly good, was not written either. The
        // database, not the counter, is what proves it.
        assertThat(result.imported()).isZero();
        assertThat(contactsOf(rep)).isEmpty();
    }

    @Test
    public void import_reportsEachMissingRequiredColumn() throws IOException {

        // given — lastName is present, firstName and account are not
        CrmUser rep = newUser("cimp14", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(rep, "lastName,email", "Lovelace,ada@example.com");

        // then — one error per missing column, none for the one that IS present, and
        // the column spelled the way the export writes it and Bean Validation reports
        // it, so the error table never shows the same column two different ways
        assertThat(result.errors()).extracting(ImportError::column)
                .containsExactlyInAnyOrder("firstName", "account");
        assertThat(result.errors()).extracting(ImportError::line).containsOnly(1L);
        assertThat(result.imported()).isZero();
    }

    @Test
    public void import_enforcesTheEmailFormatFromTheDto() throws IOException {

        // given
        CrmUser rep = newUser("cimp15", Roles.SALES_REP);
        newAccount(rep, "Acme");

        // when
        ImportResult result = importCsv(rep, HEADER, "Ada,Lovelace,not-an-email,,,Acme");

        // then — @Email is declared once, on ContactCreateRequest, and enforced
        // identically for JSON and CSV
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("email");
    }

    @Test
    public void import_allowsTwoContactsWithTheSameName() throws IOException {

        // given
        CrmUser rep = newUser("cimp16", Roles.SALES_REP);
        newAccount(rep, "Acme");

        // when
        ImportResult result = importCsv(rep, HEADER,
                "John,Smith,,,,Acme",
                "John,Smith,,,,Acme");

        // then — deliberately NOT an error. Two John Smiths at one company may be two
        // real people, and email is optional, so there is no reliable key to
        // deduplicate on. A check built on an unreliable key rejects legitimate files.
        assertThat(result.errors()).isEmpty();
        assertThat(result.imported()).isEqualTo(2);
    }
}
