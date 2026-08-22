package com.berkay.crm.service;

import com.berkay.crm.dto.ActivityCreateRequest;
import com.berkay.crm.dto.ActivityResponse;
import com.berkay.crm.dto.ActivityUpdateRequest;
import com.berkay.crm.exception.ConflictException;
import com.berkay.crm.exception.ResourceNotFoundException;
import com.berkay.crm.model.*;
import com.berkay.crm.repository.ActivityRepository;
import com.berkay.crm.repository.specification.ActivitySpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;

    private final AccountService accountService;

    private final ContactService contactService;

    public ActivityService(ActivityRepository activityRepository, AccountService accountService, ContactService contactService) {
        this.activityRepository = activityRepository;
        this.accountService = accountService;
        this.contactService = contactService;
    }

    @Transactional
    public ActivityResponse create(Long accountId, ActivityCreateRequest request, CrmUser user) {

        validateTypeRules(request.type(), request.dueAt());

        Account account = accountService.loadAccessible(accountId, user);
        Contact contact = resolveContact(request.contactId(), account, user);

        Activity activity = new Activity();
        activity.setAccount(account);
        activity.setContact(contact);
        activity.setType(request.type());
        activity.setSubject(request.subject());
        activity.setNotes(request.notes());
        activity.setOccurredAt(request.occurredAt());
        activity.setDueAt(request.dueAt());
        activity.setCompleted(false);

        return ActivityResponse.from(activityRepository.save(activity));
    }

    @Transactional
    public ActivityResponse update(Long activityId, ActivityUpdateRequest request, CrmUser user) {

        validateTypeRules(request.type(), request.dueAt());

        Activity activity = loadAccessible(activityId, user);

        if (!Objects.equals(activity.getVersion(), request.version())) {
            throw new ConflictException(
                    "This activity changed since you opened it — reload and try again");
        }

        Contact contact = resolveContact(request.contactId(), activity.getAccount(), user);

        activity.setType(request.type());
        activity.setSubject(request.subject());
        activity.setNotes(request.notes());
        activity.setOccurredAt(request.occurredAt());
        activity.setDueAt(request.dueAt());
        activity.setContact(contact);
        activity.setCompleted(request.completed());

        return ActivityResponse.from(activity);
    }

    @Transactional
    public void delete(Long activityId, CrmUser user) {

        Activity activity = loadAccessible(activityId, user);
        activityRepository.delete(activity);
    }

    @Transactional(readOnly = true)
    public Page<ActivityResponse> findByAccount(
            Long accountId, Pageable pageable,
            CrmUser user, ActivityType type) {

        accountService.loadAccessible(accountId, user); // auth guard

        Specification<Activity> spec = ActivitySpecifications.inAccount(accountId);
        if (type != null) {
            spec = spec.and(ActivitySpecifications.ofType(type));
        }

        return activityRepository.findAll(spec, pageable).map(ActivityResponse::from);
    }

    @Transactional(readOnly = true)
    public ActivityResponse findById(Long activityId, CrmUser user) {

        Activity activity = loadAccessible(activityId, user);

        return ActivityResponse.from(activity);
    }

    private Activity loadAccessible(Long id, CrmUser currentUser) {

        Activity activity = activityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Activity not found with id: " + id));

        // authorization is inherited
        accountService.loadAccessible(activity.getAccount().getId(), currentUser);

        return activity;
    }

    private void validateTypeRules(ActivityType type, LocalDateTime dueAt) {
        if (type != ActivityType.TASK && dueAt != null) {
            throw new IllegalArgumentException("Only TASK activities can have a due date");
        }
    }

    private Contact resolveContact(Long contactId, Account account, CrmUser user) {
        if (contactId == null) {
            return null;
        }
        Contact contact = contactService.loadAccessible(contactId, user);
        if (!contact.getAccount().getId().equals(account.getId())) {
            throw new IllegalArgumentException(
                    "Contact " + contactId + " does not belong to account " + account.getId());
        }
        return contact;
    }
}
