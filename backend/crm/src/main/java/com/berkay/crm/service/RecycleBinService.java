package com.berkay.crm.service;

import com.berkay.crm.dto.DeletedAccountResponse;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.Account;
import com.berkay.crm.repository.AccountRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one place in the application that can see past @SQLRestriction.
 * That restriction is unconditional by design — the failure mode of forgetting it
 * is "cannot see deleted rows", not "leaked deleted rows". Native SQL is the only
 * thing Hibernate does not decorate, which makes the exception visible and local
 * instead of a filter someone forgets to switch on.
 */
@Service
public class RecycleBinService {

    private final AccountRepository accountRepository;

    private final EntityManager entityManager;

    public RecycleBinService(AccountRepository accountRepository, EntityManager entityManager) {
        this.accountRepository = accountRepository;
        this.entityManager = entityManager;
    }

    @Transactional(readOnly = true)
    public List<DeletedAccountResponse> deletedAccounts() {

        List<AccountRepository.DeletedAccountRow> rows = accountRepository.findDeleted();
        if (rows.isEmpty()) {
            return List.of();
        }

        // One query for every deleter, not one per row — the same batching as
        // ContactRepository.countByAccountIdIn.
        List<Long> ids = rows.stream().map(AccountRepository.DeletedAccountRow::getId).toList();

        Map<Long, String> deletedBy = accountRepository.findDeletedBy(ids).stream()
                .collect(Collectors.toMap(
                        AccountRepository.DeletedByRow::getId,
                        AccountRepository.DeletedByRow::getDeletedBy,
                        // An account deleted, restored and deleted again has two DEL
                        // revisions. Before restore existed this could not happen, so
                        // the feature being built here is what makes the merge needed.
                        // The query orders by rev, so the later deletion wins.
                        (earlier, later) -> later));

        return rows.stream()
                .map(row -> new DeletedAccountResponse(
                        row.getId(), row.getName(), row.getIndustry(),
                        row.getDeletedAt(), row.getOwnerName(),
                        // null for anything deleted before Envers was switched on —
                        // @SQLDelete never wrote last_modified_by, so there is no
                        // other place that fact could have been recorded.
                        deletedBy.get(row.getId())))
                .toList();
    }

    @Transactional
    public void restore(Long id) {

        Account account;
        try {
            // The entity result type is the point. Native SQL gets us past
            // @SQLRestriction on the READ, and what comes back is a MANAGED entity —
            // so the write below is an ordinary dirty-check UPDATE that Envers,
            // @Version and JPA auditing all see. A native UPDATE would have skipped
            // every one of them and restored the record with no trace of the restore.
            account = (Account) entityManager
                    .createNativeQuery(
                            "select * from account where id = :id and deleted_at is not null",
                            Account.class)
                    .setParameter("id", id)
                    .getSingleResult();

        } catch (NoResultException ex) {
            // covers both "no such account" and "that account is not deleted"
            throw new ResourceNotFoundException("No deleted account with id: " + id);
        }

        // Deliberately does NOT cascade to contacts, activities or deals. deleted_at
        // cannot distinguish "removed by that account's cascade" from "deleted on
        // purpose last month", so restoring children would resurrect records someone
        // meant to be gone. Delete cascades and restore does not; the UI says so.
        account.setDeletedAt(null);
    }
}
