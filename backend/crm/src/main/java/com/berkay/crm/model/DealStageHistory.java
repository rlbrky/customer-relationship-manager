package com.berkay.crm.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "deal_stage_history")
public class DealStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deal_id", nullable = false)
    private Deal deal;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DealStage fromStage;

    @Enumerated(EnumType.STRING)
    @Column(length = 20, nullable = false)
    private DealStage toStage;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "changed_by", length = 255)
    private String changedBy;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Deal getDeal() {
        return deal;
    }

    public void setDeal(Deal deal) {
        this.deal = deal;
    }

    public DealStage getFromStage() {
        return fromStage;
    }

    public void setFromStage(DealStage fromStage) {
        this.fromStage = fromStage;
    }

    public DealStage getToStage() {
        return toStage;
    }

    public void setToStage(DealStage toStage) {
        this.toStage = toStage;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }
}
