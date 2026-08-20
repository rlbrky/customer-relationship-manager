package com.berkay.crm.service;

import com.berkay.crm.dto.DashboardSummaryResponse;
import com.berkay.crm.dto.DealResponse;
import com.berkay.crm.dto.StageSummary;
import com.berkay.crm.model.ActivityType;
import com.berkay.crm.model.CrmUser;
import com.berkay.crm.model.Deal;
import com.berkay.crm.model.DealOutcome;
import com.berkay.crm.model.DealStage;
import com.berkay.crm.repository.AccountRepository;
import com.berkay.crm.repository.ActivityRepository;
import com.berkay.crm.repository.ContactRepository;
import com.berkay.crm.repository.DealRepository;
import com.berkay.crm.repository.specification.AccountSpecifications;
import com.berkay.crm.repository.specification.ActivitySpecifications;
import com.berkay.crm.repository.specification.ContactSpecifications;
import com.berkay.crm.repository.specification.DealSpecifications;
import com.berkay.crm.security.Roles;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assembles the dashboard read model — a DTO shaped by one screen rather than by
 * one entity, which is why nothing here has a {@code from()} factory.
 */
@Service
public class DashboardService {

    /** How far ahead the "closing soon" list looks. */
    private static final int CLOSING_SOON_DAYS = 30;

    private static final int CLOSING_SOON_LIMIT = 5;

    /** A ratio, not money: scale 2 would throw away real precision. */
    private static final int WIN_RATE_SCALE = 4;

    private final DealRepository dealRepository;

    private final AccountRepository accountRepository;

    private final ContactRepository contactRepository;

    private final ActivityRepository activityRepository;

    public DashboardService(DealRepository dealRepository, AccountRepository accountRepository,
                            ContactRepository contactRepository, ActivityRepository activityRepository) {

        this.dealRepository = dealRepository;
        this.accountRepository = accountRepository;
        this.contactRepository = contactRepository;
        this.activityRepository = activityRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary(CrmUser user) {

        // The aggregate queries are raw JPQL and cannot carry a Specification, so the
        // ownership rule is re-expressed as a parameter: null means unrestricted.
        // The Specification-based reads below take the user and branch internally.
        Long ownerId = Roles.isPrivileged(user) ? null : user.getId();

        List<StageSummary> pipeline = pipelineByStage(ownerId);
        ClosedTotals closed = closedByOutcome(ownerId);

        // Four numbers, summed in Java. Fifty thousand would have to be summed in SQL —
        // that distinction is the whole reason pipelineByStage exists.
        long openDealCount = pipeline.stream()
                .mapToLong(StageSummary::dealCount).sum();

        BigDecimal openPipelineValue = pipeline.stream()
                .map(StageSummary::totalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new DashboardSummaryResponse(
                accountRepository.count(AccountSpecifications.visibleTo(user)),
                contactRepository.count(ContactSpecifications.visibleTo(user)),
                openDealCount,
                openPipelineValue,
                closed.wonCount(),
                closed.wonValue(),
                closed.lostCount(),
                winRate(closed.wonCount(), closed.lostCount()),
                overdueTaskCount(user),
                pipeline,
                closingSoon(user)
        );
    }

    /**
     * GROUP BY returns only stages that have deals, so a stage with none is absent
     * rather than zero. Walking the enum both fills those gaps and imposes board
     * order — neither of which the query promises.
     */
    private List<StageSummary> pipelineByStage(Long ownerId) {

        // No merge function on purpose: GROUP BY guarantees one row per stage, so a
        // duplicate key would be a genuine bug worth hearing about.
        Map<DealStage, DealRepository.StageTotal> rows = dealRepository.pipelineByStage(ownerId)
                .stream()
                .collect(Collectors.toMap(DealRepository.StageTotal::getStage, Function.identity()));

        return Arrays.stream(DealStage.values())
                .map(stage -> {
                    DealRepository.StageTotal row = rows.get(stage);
                    return row == null
                            ? new StageSummary(stage, 0L, BigDecimal.ZERO)
                            : new StageSummary(stage, row.getDealCount(), row.getTotalValue());
                })
                .toList();
    }

    /** Won and lost totals, defaulted to zero — a fresh database has closed nothing. */
    private ClosedTotals closedByOutcome(Long ownerId) {

        Map<DealOutcome, DealRepository.OutcomeTotal> rows = dealRepository.closedByOutcome(ownerId)
                .stream()
                .collect(Collectors.toMap(DealRepository.OutcomeTotal::getOutcome, Function.identity()));

        DealRepository.OutcomeTotal won = rows.get(DealOutcome.WON);
        DealRepository.OutcomeTotal lost = rows.get(DealOutcome.LOST);

        return new ClosedTotals(
                won == null ? 0L : won.getDealCount(),
                won == null ? BigDecimal.ZERO : won.getTotalValue(),
                lost == null ? 0L : lost.getDealCount()
        );
    }

    /**
     * null, not ZERO, when nothing has closed: "no deals have closed yet" and "we
     * lose every deal" are different facts and the dashboard renders them differently.
     */
    private BigDecimal winRate(long wonCount, long lostCount) {

        long closed = wonCount + lostCount;
        if (closed == 0) {
            return null;
        }

        // divide() without a scale and RoundingMode throws ArithmeticException as soon
        // as the result does not terminate — 1 win out of 3 closed is 0.333...
        return BigDecimal.valueOf(wonCount)
                .divide(BigDecimal.valueOf(closed), WIN_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private long overdueTaskCount(CrmUser user) {

        // dueAt is a LocalDateTime — wall clock, no zone. Comparing it against an
        // Instant compiles fine and is silently wrong by the viewer's UTC offset.
        return activityRepository.count(
                ActivitySpecifications.visibleTo(user)
                        .and(ActivitySpecifications.ofType(ActivityType.TASK))
                        .and(ActivitySpecifications.isCompleted(false))
                        .and(ActivitySpecifications.dueBefore(LocalDateTime.now())));
    }

    private List<DealResponse> closingSoon(CrmUser user) {

        Specification<Deal> spec = DealSpecifications.visibleTo(user)
                .and(DealSpecifications.isOpen(true))
                .and(DealSpecifications.expectedCloseOnOrBefore(
                        LocalDate.now().plusDays(CLOSING_SOON_DAYS)));

        // This overload carries @EntityGraph("account"), so DealResponse.from can read
        // the account name without a query per row — and without OSIV to fall back on.
        return dealRepository
                .findAll(spec, PageRequest.of(0, CLOSING_SOON_LIMIT, Sort.by("expectedCloseDate")))
                .map(DealResponse::from)
                .getContent();
    }

    /** Internal carrier — never leaves the service, so it stays out of dto/. */
    private record ClosedTotals(long wonCount, BigDecimal wonValue, long lostCount) {}
}
