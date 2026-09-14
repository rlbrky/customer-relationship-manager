package com.berkay.crm.service;

import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.specification.ContactSpecifications;
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

@Service
public class ContactExportService {

    private static final String[] HEADERS = {"firstName", "lastName", "email", "phone", "jobTitle", "account"};

    private static final int BATCH_SIZE = 100;

    private final ContactRepository contactRepository;

    public ContactExportService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Transactional(readOnly = true)
    public void writeCsv(Writer writer, CrmUser user, String query) throws IOException {

        Specification<Contact> spec = ContactSpecifications.forFilters(user, query);

        try (CsvWriter csv = new CsvWriter(writer, HEADERS)) {

            // id is the tiebreaker that makes the order not be written twice or be skipped.
            Pageable pageable = PageRequest.of(0, BATCH_SIZE, Sort.by("lastName", "firstName", "id"));

            while (true) {

                Page<Contact> batch = contactRepository.findAll(spec, pageable);

                for (Contact contact : batch) {

                    csv.row(
                            contact.getFirstName(),
                            contact.getLastName(),
                            contact.getEmail(),
                            contact.getPhone(),
                            contact.getJobTitle(),
                            contact.getAccount().getName()
                    );
                }

                if (!batch.hasNext()) {
                    break;
                }
                pageable = batch.nextPageable();
            }
        }
    }
}
