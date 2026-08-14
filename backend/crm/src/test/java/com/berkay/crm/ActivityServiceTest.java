package com.berkay.crm;

import com.berkay.crm.dto.ActivityCreateRequest;
import com.berkay.crm.dto.ActivityResponse;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.ActivityRepository;
import com.berkay.crm.service.AccountService;
import com.berkay.crm.service.ActivityService;
import com.berkay.crm.service.ContactService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ActivityServiceTest {

    @Mock
    ActivityRepository activityRepository;

    @Mock
    AccountService accountService;

    @Mock
    ContactService contactService;

    @InjectMocks
    ActivityService activityService;

    private CrmUser userWith(Long id) {
        CrmUser user = new CrmUser();
        user.setId(id);
        user.setUsername("user" + id);
        Role role = new Role();
        role.setName("ROLE_SALES_REP");
        user.getRoles().add(role);
        return user;
    }

    private Account accountWith(Long id, CrmUser owner) {
        Account account = new Account();
        account.setId(id);
        account.setName("Acme");
        account.setOwner(owner);
        return account;
    }

    private Contact contactOn(Long id, Account account) {
        Contact contact = new Contact();
        contact.setId(id);
        contact.setFirstName("Jane");
        contact.setLastName("Doe");
        contact.setAccount(account);
        return contact;
    }

    private ActivityCreateRequest request(ActivityType type, LocalDateTime dueAt, Long contactId) {
        return new ActivityCreateRequest(type, "Subject", null, Instant.now(), dueAt, contactId);
    }

    @Test
    void create_rejectsDueDateOnNonTaskType() {

        // given
        CrmUser user = userWith(1L);

        // when / then
        assertThatThrownBy(() -> activityService.create(
                10L, request(ActivityType.CALL, LocalDateTime.now().plusDays(1), null), user))
                .isInstanceOf(IllegalArgumentException.class);

        // validation runs before anything is loaded — fail fast, no wasted queries
        verify(accountService, never()).loadAccessible(any(), any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void create_allowsDueDateOnTask() {

        // given
        CrmUser user = userWith(1L);
        given(accountService.loadAccessible(10L, user)).willReturn(accountWith(10L, user));
        given(activityRepository.save(any(Activity.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ActivityResponse response = activityService.create(
                10L, request(ActivityType.TASK, LocalDateTime.now().plusDays(1), null), user);

        // then
        assertThat(response.type()).isEqualTo(ActivityType.TASK);
        assertThat(response.dueAt()).isNotNull();
    }

    @Test
    void create_allowsNullContact() {

        // given
        CrmUser user = userWith(1L);
        given(accountService.loadAccessible(10L, user)).willReturn(accountWith(10L, user));
        given(activityRepository.save(any(Activity.class))).willAnswer(inv -> inv.getArgument(0));

        // when
        ActivityResponse response = activityService.create(
                10L, request(ActivityType.NOTE, null, null), user);

        // then
        assertThat(response.contactId()).isNull();
        verifyNoInteractions(contactService);   // an unlinked activity never touches contacts
    }

    @Test
    void create_rejectsContactFromAnotherAccount() {

        // given
        CrmUser user = userWith(1L);
        Account target = accountWith(10L, user);
        Account other = accountWith(20L, user);
        given(accountService.loadAccessible(10L, user)).willReturn(target);
        given(contactService.loadAccessible(5L, user)).willReturn(contactOn(5L, other));

        // when
        assertThatThrownBy(() -> activityService.create(
                10L, request(ActivityType.CALL, null, 5L), user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to account");

        // then
        verify(activityRepository, never()).save(any());
    }

    @Test
    void findByAccount_propagatesAccessDenied() {

        // given
        CrmUser user = userWith(1L);
        given(accountService.loadAccessible(10L, user))
                .willThrow(new AccessDeniedException("denied"));

        // when
        assertThatThrownBy(() -> activityService.findByAccount(10L, Pageable.unpaged(), user, null))
                .isInstanceOf(AccessDeniedException.class);

        // then
        verify(activityRepository, never())
                .findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    void delete_callsRepositoryDeleteForSoftDelete() {

        // given
        CrmUser user = userWith(1L);
        Activity activity = new Activity();
        activity.setId(7L);
        activity.setAccount(accountWith(10L, user));
        given(activityRepository.findById(7L)).willReturn(Optional.of(activity));

        activityService.delete(7L, user);

        // when / then
        verify(activityRepository).delete(activity);
    }
}
