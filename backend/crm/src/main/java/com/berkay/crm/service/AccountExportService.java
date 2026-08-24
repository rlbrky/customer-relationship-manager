package com.berkay.crm.service;

import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.specification.AccountSpecifications;
import com.berkay.crm.service.csv.CsvWriter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AccountExportService {

    /**
     * Everything meaningful, so a file exported here can be fed back through the
     * importer in 12b. owner is the USERNAME, not the id: a human reads this, and
     * the importer resolves it back by name.
     */
    private static final String[] HEADERS =
            {"name", "industry", "website", "phone", "owner", "contactCount", "createdDate"};

    /** Rows per query. Memory scales with this, never with the size of the export. */
    private static final int BATCH_SIZE = 200;

    private final AccountRepository accountRepository;

    private final ContactRepository contactRepository;

    public AccountExportService(AccountRepository accountRepository,
                                ContactRepository contactRepository) {
        this.accountRepository = accountRepository;
        this.contactRepository = contactRepository;
    }

    /**
     * Writes the export straight into the caller's Writer.
     *
     * Taking a Writer rather than returning a String is the whole design: the file
     * never exists in memory. Doing this synchronously inside a transactional method
     * also sidesteps StreamingResponseBody, which runs after the controller returns —
     * by which point, with open-in-view off, the transaction is long closed.
     */
    @Transactional(readOnly = true)
    public void writeCsv(Writer writer, CrmUser user,
                         String name, String industry, Long ownerId) throws IOException {

        // The same chain the list endpoint uses, including the visibility seed.
        Specification<Account> spec = AccountSpecifications.forFilters(user, name, industry, ownerId);

        try (CsvWriter csv = new CsvWriter(writer, HEADERS)) {

            // Sorted so the file is stable between exports. An unsorted paged read has
            // no defined order across pages, so rows can repeat or vanish as data shifts.
            Pageable page = PageRequest.of(0, BATCH_SIZE, Sort.by("name"));

            while (true) {
                // this overload carries @EntityGraph("owner"), so the username column
                // costs no extra query per row
                Page<Account> batch = accountRepository.findAll(spec, page);

                Map<Long, Long> counts = contactCounts(batch.getContent());

                for (Account account : batch) {
                    csv.row(
                            account.getName(),
                            account.getIndustry(),
                            account.getWebsite(),
                            account.getPhone(),
                            account.getOwner().getUsername(),
                            counts.getOrDefault(account.getId(), 0L),
                            account.getCreatedDate());
                }

                if (!batch.hasNext()) {
                    break;
                }
                page = batch.nextPageable();
            }
        }
    }

    /** One count query per batch, not per row — the M6 lesson at export scale. */
    private Map<Long, Long> contactCounts(List<Account> accounts) {

        List<Long> ids = accounts.stream().map(Account::getId).toList();

        // `in :ids` with an empty collection is invalid SQL on some databases
        if (ids.isEmpty()) {
            return Map.of();
        }

        return contactRepository.countByAccountIdIn(ids).stream()
                .collect(Collectors.toMap(
                        ContactRepository.AccountContactCount::getAccountId,
                        ContactRepository.AccountContactCount::getTotal));
    }
}
