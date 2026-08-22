package com.berkay.crm.model;

import com.berkay.crm.security.CurrentUsername;
import org.hibernate.envers.RevisionListener;

public class AuditRevisionListener implements RevisionListener {

    @Override
    public void newRevision(Object revisionEntity) {
        ((AuditRevision) revisionEntity).setUsername(CurrentUsername.get());
    }
}
