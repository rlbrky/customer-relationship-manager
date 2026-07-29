package com.berkay.crm.service;

import com.berkay.crm.dto.AccountCreateRequest;
import com.berkay.crm.dto.AccountResponse;
import com.berkay.crm.dto.AccountUpdateRequest;
import com.berkay.crm.exception.ConflictException;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.Principal;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    private final UserRepository userRepository;

    public AccountService(AccountRepository accountRepository, UserRepository userRepository) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse create(AccountCreateRequest request, CrmUser currentUser) {

        Account account = new Account();
        account.setName(request.name());
        account.setIndustry(request.industry());
        account.setWebsite(request.website());
        account.setPhone(request.phone());
        account.setOwner(resolveOwner(request.ownerId(), currentUser));

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(Long id, AccountUpdateRequest request, CrmUser currentUser) {

        Account account = loadAccessible(id, currentUser);

        account.setName(request.name());
        account.setIndustry(request.industry());
        account.setWebsite(request.website());
        account.setPhone(request.phone());
        account.setOwner(resolveOwner(request.ownerId(), currentUser));

        return AccountResponse.from(account); // managed
    }

    @Transactional
    public void delete(Long id, CrmUser currentUser) {

        Account account = loadAccessible(id, currentUser);
        accountRepository.delete(account); // @SQLDelete turns this into a soft delete
    }

    @Transactional(readOnly = true)
    public Page<AccountResponse> findAll(Pageable pageable, CrmUser currentUser) {

        // This filtering can't be made with annotations
        Page<Account> accounts = isPrivileged(currentUser)
                ? accountRepository.findAll(pageable)
                : accountRepository.findAllByOwnerId(currentUser.getId(), pageable);

        return accounts.map(AccountResponse::from);
    }

    @Transactional(readOnly = true)
    public AccountResponse findById(Long id, CrmUser currentUser) {

        Account account = loadAccessible(id, currentUser);
        return AccountResponse.from(account);
    }

    // Load or 404, THEN Authorize or 403 - never the opposite!
    private Account loadAccessible(Long id, CrmUser currentUser) {

        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));

        boolean isOwner = account.getOwner().getId().equals(currentUser.getId());

        if (!isOwner && !isPrivileged(currentUser)) {
            throw new AccessDeniedException("You do not have access to this account");
        }

        return account;
    }

    // null ownerId means "me". Assigning someone else is a privileged action.
    private CrmUser resolveOwner(Long ownerId, CrmUser currentUser) {

        Long targetId = (ownerId == null) ? currentUser.getId() : ownerId;

        if (!targetId.equals(currentUser.getId()) && !isPrivileged(currentUser)) {
            throw new AccessDeniedException("Only managers and admins can assign a different owner");
        }

        // always re-load: the principal's CrmUser is DETACHED (loaded at login)
        return userRepository.findById(targetId)
                .orElseThrow(() -> new ResourceNotFoundException("Owner not found: " + targetId));
    }

    // Accept only ADMIN and MANAGER roles
    private boolean isPrivileged(CrmUser user) {

        return user.getRoles().stream()
                .map(Role::getName)
                .anyMatch(n -> n.equals("ROLE_ADMIN") || n.equals("ROLE_MANAGER"));
    }
}
