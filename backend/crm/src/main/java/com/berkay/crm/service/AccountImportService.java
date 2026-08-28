package com.berkay.crm.service;

import com.berkay.crm.dto.AccountCreateRequest;
import com.berkay.crm.dto.ImportError;
import com.berkay.crm.dto.ImportResult;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.security.Roles;
import com.berkay.crm.service.csv.CsvReader;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Service
public class AccountImportService {

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final Validator validator;

    public AccountImportService(AccountRepository accountRepository, UserRepository userRepository, Validator validator) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.validator = validator;
    }

    private static final Set<String> REQUIRED = Set.of("name");

    private record Pending(long line, AccountCreateRequest request, CrmUser owner) {}

    @Transactional
    public ImportResult importCsv(InputStream in, CrmUser currentUser) throws IOException {

        CsvReader reader = CsvReader.parse(in);

        if(!reader.headers().containsAll(REQUIRED)) {
            return new ImportResult(0, 0, List.of(
                    new ImportError(1, "name", "Required column is missing")
            ));
        }

        List<ImportError> errors = new ArrayList<>();
        List<Pending> pendings = new ArrayList<>();
        Map<String, Long> seenNames = new HashMap<>();

        for (CsvReader.CsvRow row : reader.rows()) {

            AccountCreateRequest request = new AccountCreateRequest(
                    row.get("name"),
                    row.get("industry"),
                    row.get("website"),
                    row.get("phone"),
                    null);

            boolean rowFailed = false;

            for (ConstraintViolation<AccountCreateRequest> violation : validator.validate(request)) {
                errors.add(new ImportError(
                        row.line(),
                        violation.getPropertyPath().toString(),
                        violation.getMessage()));

                rowFailed = true;
            }

            CrmUser owner = currentUser;
            String username = row.get("owner");

            if (username != null) {

                Optional<CrmUser> found = userRepository.findByUsername(username);

                if (found.isEmpty()) {

                    errors.add(new ImportError(row.line(), "owner", "No user named " + username));
                    rowFailed = true;
                } else if (!found.get().getId().equals(currentUser.getId())
                        && !Roles.isPrivileged(currentUser)) {

                    // Both halves matter: a DIFFERENT owner, and a caller who is not
                    // privileged. Without the second clause a manager cannot do the
                    // thing managers exist to do.
                    errors.add(new ImportError(row.line(), "owner", "Only managers and admins can assign a different owner"));
                    rowFailed = true;
                } else {
                    owner = found.get();
                }
            }

            if(request.name() != null) {

                // Will return existing value or null
                Long firstSeen = seenNames.putIfAbsent(request.name().toLowerCase(Locale.ROOT), row.line());
                if(firstSeen != null) {

                    errors.add(new ImportError(row.line(), "name", "Duplicate of line " + firstSeen));
                    rowFailed = true;
                }
            }

            if(!rowFailed) {

                pendings.add(new Pending(row.line(), request, owner));
            }
        }

        if (!errors.isEmpty()) {
            return new ImportResult(reader.rows().size(), 0, errors);
        }

        for (Pending pending : pendings) {

            Account account = new Account();
            account.setName(pending.request().name());
            account.setIndustry(pending.request().industry());
            account.setWebsite(pending.request().website());
            account.setPhone(pending.request().phone());
            account.setOwner(pending.owner());
            accountRepository.save(account);
        }

        return new ImportResult(pendings.size(), pendings.size(), List.of());
    }
}
