package com.berkay.crm.service;

import com.berkay.crm.dto.ContactCreateRequest;
import com.berkay.crm.dto.ImportError;
import com.berkay.crm.dto.ImportResult;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.specification.AccountSpecifications;
import com.berkay.crm.service.csv.CsvReader;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ContactImportService {

    private static final Set<String> REQUIRED = Set.of("firstName", "lastName", "account");

    private final ContactRepository contactRepository;
    private final AccountRepository accountRepository;
    private final Validator validator;

    public ContactImportService(ContactRepository contactRepository, AccountRepository accountRepository, Validator validator) {
        this.contactRepository = contactRepository;
        this.accountRepository = accountRepository;
        this.validator = validator;
    }

    private record Pending(long line, ContactCreateRequest request, Account account) {}

    @Transactional
    public ImportResult importCsv(InputStream in, CrmUser currentUser) throws IOException {

        CsvReader reader = CsvReader.parse(in);

        // header check - one error per Missing COLUMN
        List<ImportError> missing = REQUIRED.stream()
                .filter(column -> !reader.headers().contains(column.toLowerCase(Locale.ROOT)))
                .map(column -> new ImportError(1, column, "Required column is missing"))
                .toList();

        if (!missing.isEmpty()) {
            return new ImportResult(0, 0, missing);
        }

        // pass 0 - every distinct account name -> one query
        Set<String> names = reader.rows().stream()
                .map(row -> row.get("account"))
                .filter(Objects::nonNull)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());

        Map<String, List<Account>> byName = resolveAccounts(names, currentUser);

        // pass 1 - Validate every row
        List<ImportError> errors = new ArrayList<>();
        List<Pending> pendings = new ArrayList<>();

        for (CsvReader.CsvRow row : reader.rows()) {

            int errorsBefore = errors.size();

            ContactCreateRequest request = new ContactCreateRequest(
                    row.get("firstName"), row.get("lastName"), row.get("email"),
                    row.get("phone"), row.get("jobTitle")
            );

            for (ConstraintViolation<ContactCreateRequest> violation : validator.validate(request)) {
                errors.add(new ImportError(
                        row.line(),
                        violation.getPropertyPath().toString(),
                        violation.getMessage()));
            }

            Account account = null;
            String accountName = row.get("account");

            if (accountName == null) {
                errors.add(new ImportError(row.line(), "account", "Account is required"));
            } else {
                List<Account> matches = byName.getOrDefault(accountName.toLowerCase(Locale.ROOT), List.of());

                switch (matches.size()) {
                    case 0 -> errors.add(new ImportError(row.line(), "account", "No account named " + accountName));

                    case 1 -> account = matches.get(0);

                    default -> errors.add(new ImportError(row.line(), "account", "More than one account named " + accountName));
                }
            }

            if (errors.size() == errorsBefore) {
                pendings.add(new Pending(row.line(), request, account));
            }
        }

        if (!errors.isEmpty()) {
            return new ImportResult(reader.rows().size(), 0, errors);
        }

        // pass 2 - new contact per pending
        for (Pending pending : pendings) {

            Contact contact = new Contact();
            contact.setFirstName(pending.request().firstName());
            contact.setLastName(pending.request().lastName());
            contact.setEmail(pending.request().email());
            contact.setPhone(pending.request().phone());
            contact.setJobTitle(pending.request().jobTitle());
            contact.setAccount(pending.account());

            contactRepository.save(contact);
        }

        return new ImportResult(pendings.size(), pendings.size(), List.of());
    }

    private Map<String, List<Account>> resolveAccounts(Set<String> lowerCasedNames, CrmUser currentUser) {

        // in () with an empty collection is invalid SQL on some DB's so guard it
        if (lowerCasedNames.isEmpty()) {
            return Map.of();
        }

        Specification<Account> spec = AccountSpecifications.visibleTo(currentUser)
                .and(AccountSpecifications.nameIn(lowerCasedNames));

        return accountRepository.findAll(spec).stream()
                .collect(Collectors.groupingBy(account -> account.getName().toLowerCase(Locale.ROOT)));
    }
}
