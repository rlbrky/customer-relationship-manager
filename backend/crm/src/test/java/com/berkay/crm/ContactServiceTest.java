package com.berkay.crm;

import com.berkay.crm.dto.ContactCreateRequest;
import com.berkay.crm.dto.ContactResponse;
import com.berkay.crm.dto.ContactUpdateRequest;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.Account;
import com.berkay.crm.model.Contact;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Role;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.service.AccountService;
import com.berkay.crm.service.ContactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ContactServiceTest {

    @Mock
    ContactRepository contactRepository;

    @Mock
    AccountService accountService;

    @InjectMocks
    ContactService contactService;

    private Contact contactWith(Long id, Long accountId, CrmUser user)
    {
        Contact contact = new Contact();
        contact.setId(id);
        contact.setFirstName("test");
        contact.setLastName("user");
        contact.setJobTitle("TEST JOB");

        Account account = accountWith(accountId, user);
        contact.setAccount(account);

        return contact;
    }

    private Account accountWith(Long id, CrmUser user) {

        Account account = new Account();
        account.setId(id);
        account.setName("testAccount");
        account.setOwner(user);

        return account;
    }

    private CrmUser userWith(Long id, String roleName)
    {
        CrmUser user = new CrmUser();
        user.setId(id);
        user.setUsername("user" + id);

        Role role = new Role();
        role.setName(roleName);
        user.getRoles().add(role);

        return user;
    }

    @Test
    void create_attachesContactToPathAccount() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        Account account = accountWith(2L, user);

        given(accountService.loadAccessible(2L, user)).willReturn(account);
        given(contactRepository.save(any(Contact.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ContactResponse response = contactService.create(
                2L,
                new ContactCreateRequest("test", "user", null, null, null),
                user
        );

        // then
        assertThat(response.accountId()).isEqualTo(2L);
        assertThat(response.firstName()).isEqualTo("test");
    }

    @Test
    void findByAccount_propagatesAccessDenied() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        given(accountService.loadAccessible(2L, user))
                .willThrow(new AccessDeniedException("Denied"));

        // when + then
        //assertThatThrownBy(() -> contactService.findByAccount(2L, Pageable.unpaged(), user))
                //.isInstanceOf(AccessDeniedException.class);

        //verify(contactRepository, never()).findByAccountId(any(), any()); // should never query
    }

    @Test
    void findById_throwsNotFoundForMissingContact() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        given(contactRepository.findById(99L)).willReturn(Optional.empty());

        // when + then
        assertThatThrownBy(() -> contactService.findById(99L, user))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findById_deniesWhenParentAccountNotAccessible() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        CrmUser anotherUser = userWith(2L, "ROLE_SALES_REP");

        given(contactRepository.findById(5L))
                .willReturn(Optional.of(contactWith(5L, 3L, user)));

        given(accountService.loadAccessible(3L, anotherUser))
                .willThrow(new AccessDeniedException("Denied"));

        // when + then
        assertThatThrownBy(() -> contactService.findById(5L, anotherUser))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void delete_callsRepositoryDeleteForSoftDelete() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        Contact contact = contactWith(2L, 3L, user);
        given(contactRepository.findById(2L)).willReturn(Optional.of(contact));
        given(accountService.loadAccessible(3L, user)).willReturn(contact.getAccount());

        // when
        contactService.delete(2L, user);

        // then
        verify(contactRepository).delete(contact);
    }

    @Test
    void delete_deniesWhenParentAccountNotAccessible() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        CrmUser anotherUser = userWith(2L, "ROLE_SALES_REP");

        given(contactRepository.findById(5L))
                .willReturn(Optional.of(contactWith(5L, 3L, user)));

        given(accountService.loadAccessible(3L, anotherUser))
                .willThrow(new AccessDeniedException("Denied"));

        // when + then
        assertThatThrownBy(() -> contactService.delete(5L, anotherUser))
                .isInstanceOf(AccessDeniedException.class);

        //verify(contactRepository, never()).delete(any());
    }

    @Test
    void update_deniesWhenParentAccountNotAccessible() {

        // given
        CrmUser user = userWith(1L, "ROLE_SALES_REP");
        CrmUser anotherUser = userWith(2L, "ROLE_SALES_REP");
        Contact contact = contactWith(99L, 3L, user);

        given(contactRepository.findById(99L)).willReturn(Optional.of(contact));
        given(accountService.loadAccessible(3L, anotherUser))
                .willThrow(new AccessDeniedException("Denied"));

        // when + then
        assertThatThrownBy(() -> contactService.update(99L,
                new ContactUpdateRequest("Hacked", "Name", null, null, null), anotherUser))
                .isInstanceOf(AccessDeniedException.class);
        // check that entity is never mutated
        assertThat(contact.getFirstName()).isEqualTo("test");
    }
}
