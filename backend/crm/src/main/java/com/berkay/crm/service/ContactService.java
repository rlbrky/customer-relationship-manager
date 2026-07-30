package com.berkay.crm.service;

import com.berkay.crm.dto.ContactCreateRequest;
import com.berkay.crm.dto.ContactResponse;
import com.berkay.crm.dto.ContactUpdateRequest;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.security.CrmUserDetails;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ContactService {

    private final ContactRepository contactRepository;

    private final AccountService accountService;

    public ContactService(ContactRepository contactRepository, AccountService accountService) {
        this.contactRepository = contactRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public Page<ContactResponse> findByAccount(Long accountId, Pageable pageable, CrmUser user) {

        // authorization guard: 403/404 propagate
        accountService.loadAccessible(accountId, user);

        Page<Contact> contacts = contactRepository.findByAccountId(accountId, pageable);

        return contacts.map(ContactResponse::from);
    }

    @Transactional(readOnly = true)
    public ContactResponse findById(Long id, CrmUser user) {

        Contact contact = loadAccessible(id, user);

        return ContactResponse.from(contact);
    }

    @Transactional
    public ContactResponse create(Long accountId, ContactCreateRequest request, CrmUser user) {

        Account account = accountService.loadAccessible(accountId, user);

        Contact contact = new Contact();
        contact.setAccount(account);
        contact.setFirstName(request.firstName());
        contact.setLastName(request.lastName());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setJobTitle(request.jobTitle());

        return ContactResponse.from(contactRepository.save(contact));
    }

    @Transactional
    public ContactResponse update(Long id, ContactUpdateRequest request, CrmUser user) {

        Contact contact = loadAccessible(id, user);

        contact.setFirstName(request.firstName());
        contact.setLastName(request.lastName());
        contact.setEmail(request.email());
        contact.setPhone(request.phone());
        contact.setJobTitle(request.jobTitle());

        return ContactResponse.from(contact); // managed so no repository save
    }

    @Transactional
    public void delete(Long id, CrmUser user) {

        Contact contact = loadAccessible(id, user);
        contactRepository.delete(contact);
    }

    private Contact loadAccessible(Long id, CrmUser currentUser) {

        Contact contact = contactRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact not found with id: " + id));

        // authorization is inherited from parent account
        accountService.loadAccessible(contact.getAccount().getId(), currentUser);

        return contact;
    }
}
