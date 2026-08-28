package com.berkay.crm;

import com.berkay.crm.dto.ImportError;
import com.berkay.crm.dto.ImportResult;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.RoleRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.repository.specification.AccountSpecifications;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.AccountImportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same isolation rule as AccountExportServiceTest: every fixture owner is a
 * SALES_REP, so visibleTo scopes each assertion to the accounts that test created.
 * The audit and recycle-bin tests commit, and their rows outlive them.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class AccountImportServiceTest {

    @Autowired private AccountImportService accountImportService;
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

    private ImportResult importCsv(CrmUser user, String... lines) throws IOException {
        String csv = String.join("\n", lines);
        return accountImportService.importCsv(
                new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)), user);
    }

    /** Only the accounts this caller can see — never a global count. */
    private List<Account> accountsOf(CrmUser owner) {
        return accountRepository.findAll(AccountSpecifications.visibleTo(owner));
    }

    @Test
    public void import_createsEveryValidRow() throws IOException {

        // given
        CrmUser owner = newUser("imp1", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(owner,
                "name,industry,website,phone",
                "Acme,Technology,https://acme.test,555-0100",
                "Globex,Manufacturing,,");

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.imported()).isEqualTo(2);

        assertThat(accountsOf(owner))
                .extracting(Account::getName)
                .containsExactlyInAnyOrder("Acme", "Globex");
    }

    @Test
    public void import_reportsEveryBadRowNotJustTheFirst() throws IOException {

        // given — rows 3 AND 4 have a blank name
        CrmUser owner = newUser("imp2", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(owner,
                "name,industry",
                "Acme,Technology",
                ",Manufacturing",
                ",Retail");

        // then — the whole point of validating in a first pass: one upload, one
        // complete list of everything to fix, rather than one typo per round trip
        assertThat(result.errors()).hasSize(2);
        assertThat(result.errors()).extracting(ImportError::line).containsExactly(3L, 4L);
        assertThat(result.errors()).extracting(ImportError::column).containsOnly("name");
    }

    @Test
    public void import_writesNothingWhenAnyRowFails() throws IOException {

        // given — one perfectly good row and one bad one
        CrmUser owner = newUser("imp3", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(owner,
                "name,industry",
                "Acme,Technology",
                ",Manufacturing");

        // then
        assertThat(result.imported()).isZero();

        // ... and this is the assertion that actually matters. imported() == 0 would
        // pass even if the good row HAD been written and the counter was simply wrong.
        assertThat(accountsOf(owner)).isEmpty();
    }

    @Test
    public void import_numbersErrorsByFileLine() throws IOException {

        // given — the bad row is the second DATA row, so line 3 in the file
        CrmUser owner = newUser("imp4", Roles.SALES_REP);

        // when — a row whose name cell is blank. NOT an empty line: Commons CSV
        // skips those entirely, so they never become rows and never produce errors.
        ImportResult result = importCsv(owner,
                "name,industry",
                "Acme,Technology",
                ",Manufacturing");

        // then — an off-by-one here points the user at the wrong row in Excel, which
        // is worse than giving them no line number at all
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).line()).isEqualTo(3L);
    }

    @Test
    public void import_reportsAMissingRequiredColumnOnce() throws IOException {

        // given — no name column at all
        CrmUser owner = newUser("imp5", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(owner,
                "industry,phone",
                "Technology,555-0100",
                "Retail,555-0200");

        // then — one error about the FILE, not one per row. Checking the header set
        // before reading any rows is what keeps a 500-row file from producing 500
        // identical complaints.
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).line()).isEqualTo(1L);
        assertThat(result.errors().get(0).column()).isEqualTo("name");
        assertThat(result.imported()).isZero();
    }

    @Test
    public void import_defaultsABlankOwnerToTheUploader() throws IOException {

        // given
        CrmUser owner = newUser("imp6", Roles.SALES_REP);

        // when — no owner column at all
        ImportResult result = importCsv(owner, "name", "Acme");

        // then — the same rule AccountService applies to a null ownerId
        assertThat(result.imported()).isEqualTo(1);
        assertThat(accountsOf(owner).get(0).getOwner().getId()).isEqualTo(owner.getId());
    }

    @Test
    public void import_assignsANamedOwnerForAManager() throws IOException {

        // given
        CrmUser manager = newUser("imp7", Roles.MANAGER);
        CrmUser rep = newUser("imp8", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(manager, "name,owner", "Acme,imp8");

        // then — the CSV names the owner by USERNAME, because a human wrote the file
        assertThat(result.errors()).isEmpty();
        assertThat(accountsOf(rep))
                .extracting(account -> account.getOwner().getUsername())
                .containsExactly("imp8");
    }

    @Test
    public void import_rejectsAnUnknownOwner() throws IOException {

        // given
        CrmUser manager = newUser("imp9", Roles.MANAGER);

        // when
        ImportResult result = importCsv(manager, "name,owner", "Acme,nobody");

        // then — a row error naming the column, not a 500 and not a silent fallback
        // to the uploader, which would quietly put the account in the wrong place
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("owner");
        assertThat(result.imported()).isZero();
    }

    @Test
    public void import_forbidsARepFromAssigningAnotherOwner() throws IOException {

        // given
        CrmUser rep = newUser("imp10", Roles.SALES_REP);
        CrmUser other = newUser("imp11", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(rep, "name,owner", "Acme,imp11");

        // then — without this check, bulk import is a privilege escalation: a rep
        // creates accounts owned by anyone simply by naming them in a file
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("owner");
        assertThat(accountsOf(other)).isEmpty();
    }

    @Test
    public void import_reportsDuplicateNamesWithinTheFile() throws IOException {

        // given
        CrmUser owner = newUser("imp12", Roles.SALES_REP);

        // when — same name twice, differing only in case
        ImportResult result = importCsv(owner,
                "name,industry",
                "Acme,Technology",
                "ACME,Retail");

        // then — Bean Validation cannot see this: it validates one object at a time.
        // The message names the earlier line, which is the difference between a
        // usable error and a puzzle.
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).line()).isEqualTo(3L);
        assertThat(result.errors().get(0).message()).contains("2");
    }

    @Test
    public void import_enforcesTheLengthLimitFromTheDto() throws IOException {

        // given — 151 characters against @Size(max = 150) on AccountCreateRequest
        CrmUser owner = newUser("imp13", Roles.SALES_REP);
        String tooLong = "x".repeat(151);

        // when
        ImportResult result = importCsv(owner, "name", tooLong);

        // then — nothing here restates the limit. It is declared once on the DTO and
        // enforced identically whether the account arrives as JSON or as CSV.
        assertThat(result.errors()).hasSize(1);
        assertThat(result.errors().get(0).column()).isEqualTo("name");
    }

    @Test
    public void import_acceptsAFileWithAByteOrderMark() throws IOException {

        // given — exactly what our own export writes
        CrmUser owner = newUser("imp14", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(owner, "﻿name,industry", "Acme,Technology");

        // then — without the BOM strip the first header is "﻿name", every
        // get("name") returns null, and our own export fails our own import while
        // looking like a malformed file
        assertThat(result.errors()).isEmpty();
        assertThat(result.imported()).isEqualTo(1);
    }

    @Test
    public void import_ignoresColumnsItDoesNotUnderstand() throws IOException {

        // given — contactCount and createdDate come back from our own export
        CrmUser owner = newUser("imp15", Roles.SALES_REP);

        // when
        ImportResult result = importCsv(owner,
                "name,industry,contactCount,createdDate",
                "Acme,Technology,7,2026-08-21T09:14:03Z");

        // then — rejecting the file for extra columns would break the round trip
        assertThat(result.errors()).isEmpty();
        assertThat(result.imported()).isEqualTo(1);
    }

    @Test
    public void import_handlesAHeaderOnlyFile() throws IOException {

        // given
        CrmUser owner = newUser("imp16", Roles.SALES_REP);

        // when — valid, just empty. Not an error.
        ImportResult result = importCsv(owner, "name,industry");

        // then
        assertThat(result.errors()).isEmpty();
        assertThat(result.totalRows()).isZero();
        assertThat(result.imported()).isZero();
    }
}
