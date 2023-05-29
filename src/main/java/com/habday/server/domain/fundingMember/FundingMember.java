package com.habday.server.domain.fundingMember;

import com.habday.server.constants.FundingState;
import com.habday.server.constants.ScheduledPayState;
import com.habday.server.domain.fundingItem.FundingItem;
import com.habday.server.domain.member.Member;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.DynamicInsert;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@DynamicInsert
@Getter
@NoArgsConstructor
@Entity
@Table(name = "FUNDING_MEMBER")
public class FundingMember{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fundingMemberId")
    private Long id;

    @Column
    private String name;

    @Column
    private BigDecimal amount;

    @Column
    private String message;

    @Column
    private LocalDate fundingDate;

    @Column
    private Long paymentId;

    @Column
    @Enumerated(value = EnumType.STRING)
    private ScheduledPayState payment_status;

    @Column(name = "merchant_id")
    private String merchantId;

    @Column(name = "imp_uid")
    private String impUid;

    @ColumnDefault("0")
    @Column(name = "cancel_amount")
    private BigDecimal cancelAmount;

    @Column
    private String reason;

    @Column
    private LocalDate cancelDate;

    @Column
    private String fail_reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fundingItemId")
    private FundingItem fundingItem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "memberId")
    private Member member;

    @Builder
    public FundingMember(String name, BigDecimal amount, String message, LocalDate fundingDate, Long paymentId,
                         String merchantId, String impUid, FundingItem fundingItem, Member member,
                         ScheduledPayState payment_status){
        this.name = name;
        this.amount = amount;
        this.message = message;
        this.fundingDate = fundingDate;
        this.paymentId = paymentId;
        this.payment_status = payment_status;
        this.merchantId = merchantId;
        this.impUid = impUid;
        this.fundingItem = fundingItem;
        this.member = member;
    }

    public FundingMember updateCancel(BigDecimal cancelAmount, String reason, ScheduledPayState payment_status, LocalDate cancelDate){
        this.cancelAmount = cancelAmount;
        this.reason = reason;
        this.payment_status = payment_status;
        this.cancelDate = cancelDate;
        return this;
    }

    public FundingMember updateWebhookFail(ScheduledPayState payment_status, String fail_reason){
        this.payment_status = payment_status;
        this.fail_reason = fail_reason;
        return this;
    }

}
