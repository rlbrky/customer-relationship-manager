package com.berkay.crm;

import com.berkay.crm.dto.AccountCreateRequest;
import com.berkay.crm.dto.AccountResponse;
import com.berkay.crm.dto.AccountUpdateRequest;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ActivityRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.UserRepository;
import com.berkay.crm.service.AccountService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    AccountRepository accountRepository;

    @Mock
    ContactRepository contactRepository;

    @Mock
    ActivityRepository activityRepository;

    @Mock
    UserRepository userRepository;

    @InjectMocks
    AccountService accountService;

    private CrmUser userWith(Long id, String roleName) {

        CrmUser user = new CrmUser();
        user.setId(id);
        user.setUsername("user" + id);

        Role role = new Role();
        role.setName(roleName);
        user.getRoles().add(role);

        return user;
    }

    private Account accountOwnedBy(Long id, CrmUser owner) {

        Account account = new Account();
        account.setId(id);
        account.setName("testAccount");
        account.setOwner(owner);

        return account;
    }

    @Test
    void create_assignsCurrentUserAsOwnerWhenUnspecified() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(accountRepository.save(any(Account.class)))
                .willAnswer(inv -> inv.getArgument(0));

        // when
        AccountResponse response = accountService.create(
                // omitted ownerId
                new AccountCreateRequest("testAccount", null, null, null, null),
                user
        );

        // then
        assertThat(response.ownerId()).isEqualTo(1L);
    }

    @Test
    void create_rejetsSalesRepAssigningAnotherOwner() {

        // given
        CrmUser rep = userWith(1l, "ROLE_SALES_REP");

        // when + then
        assertThatThrownBy(() -> accountService.create(
                new AccountCreateRequest("testAccount", null, null, null, 99L),
                rep
        )).isInstanceOf(AccessDeniedException.class);

        verify(accountRepository, never()).save(any(Account.class));
    }

    @Test
    void update_rejectsSalesRepEditingSomeoneElsesAccount() {

        // given
        CrmUser owner = userWith(1L, "ROLE_SALES_REP");
        CrmUser otherRep = userWith(2L, "ROLE_SALES_REP");
        given(accountRepository.findById(99L)).willReturn(Optional.of(accountOwnedBy(99L, owner)));

        // when + then
        assertThatThrownBy(() -> accountService.update(99L,
                new AccountUpdateRequest("testAccount", null, null, null, 2L), otherRep))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void update_allowsManagerOnAnyAccount() {

        // given
        CrmUser owner = userWith(1L, "ROLE_SALES_REP");
        CrmUser manager = userWith(2L, "ROLE_MANAGER");
        given(accountRepository.findById(99L)).willReturn(Optional.of(accountOwnedBy(99L, owner)));
        given(userRepository.findById(1L)).willReturn(Optional.of(owner));

        // when
        AccountResponse response = accountService.update(99L,
                new AccountUpdateRequest("testAccount Renamed", null, null, null, 1L), manager);

        // then
        assertThat(response.name()).isEqualTo("testAccount Renamed");
    }

    @Test
    void findById_throwsNotFoundWhenMissingOrSoftDeleted() {

        // given
        CrmUser rep = userWith(1L, "ROLE_SALES_REP");
        given(accountRepository.findById(99L)).willReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> accountService.findById(99L, rep))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void delete_callsRepositoryDeleteForSoftDelete() {

        // given
        CrmUser rep = userWith(1L, "ROLE_SALES_REP");
        Account account = accountOwnedBy(99L, rep);
        given(accountRepository.findById(99L)).willReturn(Optional.of(account));

        // when
        accountService.delete(99L, rep);

        // then
        verify(accountRepository).delete(account);  // @SQLDelete makes this a soft delete
    }

    @Test
    void delete_alsoSoftDeletesContacts() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        Account account = accountOwnedBy(10L, user);

        Contact first = new Contact();
        Contact second = new Contact();
        account.getContacts().addAll(List.of(first, second));

        Activity activity = new Activity();
        account.getActivities().add(activity);

        given(accountRepository.findById(10L)).willReturn(Optional.of(account));

        // when
        accountService.delete(10L, user);

        // then
        verify(activityRepository).delete(activity);
        verify(contactRepository).delete(first);
        verify(contactRepository).delete(second);
        verify(accountRepository).delete(account);
    }
}
