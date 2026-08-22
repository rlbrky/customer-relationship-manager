package com.berkay.crm.service;

import com.berkay.crm.dto.FieldChange;
import com.berkay.crm.dto.RevisionResponse;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.AuditRevision;
import com.berkay.crm.model.CrmUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.hibernate.ObjectNotFoundException;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Reads an account's edit history out of the Envers audit tables.
 *
 * Separate from AccountService on purpose: this reads audit infrastructure rather
 * than managing accounts, and the two have no logic in common.
 */
@Service
public class AccountAuditService {

    /** The shared transactional proxy Spring exposes — not a raw EntityManager. */
    private final EntityManager entityManager;

    private final AccountService accountService;

    public AccountAuditService(EntityManager entityManager, AccountService accountService) {
        this.entityManager = entityManager;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<RevisionResponse> revisions(Long accountId, CrmUser user) {

        // The only thing guarding this. Everywhere else @SQLRestriction quietly
        // backstops a missing check; AuditReader reads the _AUD tables directly and
        // no restriction applies, so forgetting this line hands any signed-in rep
        // the full edit history of every account in the organisation.
        accountService.loadAccessible(accountId, user);

        AuditReader reader = AuditReaderFactory.get(entityManager);

        @SuppressWarnings("unchecked")
        List<Object[]> rows = reader.createQuery()
                // (entity, revision, type) triples rather than bare snapshots
                .forRevisionsOfEntity(Account.class, false, true)
                .add(AuditEntity.id().eq(accountId))
                // ascending because each diff needs its predecessor
                .addOrder(AuditEntity.revisionNumber().asc())
                .getResultList();

        List<RevisionResponse> revisions = new ArrayList<>();
        Account previous = null;

        for (Object[] row : rows) {
            Account snapshot = (Account) row[0];
            AuditRevision revision = (AuditRevision) row[1];
            RevisionType type = (RevisionType) row[2];

            revisions.add(new RevisionResponse(
                    revision.getId(),
                    Instant.ofEpochMilli(revision.getTimestamp()),
                    revision.getUsername(),
                    type.name(),
                    // Envers stores only the identifier on a delete, so diffing a DEL
                    // snapshot would report every field "changing" to null. The type
                    // already says everything there is to say.
                    type == RevisionType.DEL ? List.of() : changesBetween(previous, snapshot)
            ));

            previous = snapshot;
        }

        // Diffing needed oldest-first; a timeline reads newest-first.
        Collections.reverse(revisions);
        return revisions;
    }

    /**
     * The first revision has no predecessor. An empty Account stands in for it, so
     * every field that was set on insert shows up as an initial value instead of
     * needing a null check per field.
     */
    private List<FieldChange> changesBetween(Account before, Account after) {

        Account baseline = before == null ? new Account() : before;
        List<FieldChange> changes = new ArrayList<>();

        add(changes, "name", baseline.getName(), after.getName());
        add(changes, "industry", baseline.getIndustry(), after.getIndustry());
        add(changes, "website", baseline.getWebsite(), after.getWebsite());
        add(changes, "phone", baseline.getPhone(), after.getPhone());
        add(changes, "owner", ownerOf(baseline), ownerOf(after));
        // Not how a delete shows up — @SQLDelete rewrites the SQL, but Envers listens
        // to the lifecycle event and records DEL regardless. This is here for a future
        // restore, which is an ordinary update clearing the column.
        add(changes, "deletedAt", baseline.getDeletedAt(), after.getDeletedAt());

        return changes;
    }

    private void add(List<FieldChange> changes, String field, Object before, Object after) {
        if (!Objects.equals(before, after)) {
            changes.add(new FieldChange(field, str(before), str(after)));
        }
    }

    private String str(Object value) {
        return value == null ? null : value.toString();
    }

    /**
     * Envers was switched on over a database that already had rows, so a user who
     * has not been touched since has no audit history at all. Navigating to them
     * from an account revision then fails rather than falling back to the live row.
     * A missing owner name is worth far less than a history endpoint that throws.
     */
    private String ownerOf(Account snapshot) {
        try {
            CrmUser owner = snapshot.getOwner();
            return owner == null ? null : owner.getUsername();
        } catch (EntityNotFoundException | ObjectNotFoundException ex) {
            return null;
        }
    }
}
