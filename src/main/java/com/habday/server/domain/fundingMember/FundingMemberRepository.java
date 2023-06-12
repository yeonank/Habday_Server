package com.habday.server.domain.fundingMember;

import com.habday.server.constants.FundingState;
import com.habday.server.domain.member.Member;
import com.habday.server.dto.res.fund.GetParticipatedListResponseDto.ParticipatedListInterface;
import com.habday.server.dto.res.fund.GetParticipatedListResponseDto.ParticipatedList;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FundingMemberRepository extends JpaRepository<FundingMember, Long> {
    Long countByFundingItemIdAndMemberId(Long id, Long memberId);
    FundingMember findByMerchantId(String merchant_id);
    /*
    select * from funding_member m inner join funding_item i on m.funding_item_id = i.funding_item_id
    where m.member_id = 멤버id and m.funding_item_id = 펀딩아이템id
    */

    //status = PROGRESS
    @Query("select m.id as fundingMemberId, i.fundingItemImg as fundingItemImg, i.fundingName as fundingName, i.totalPrice as totalPrice, i.startDate as startDate, i.finishDate as finishDate, i.status as status, m.fundingDate as fundingDate, m.payment_status as payment_status, m.merchantId as merchantId from FundingMember m join m.fundingItem i where m.member = :member and i.status = :status order by m.id desc")
    List<ParticipatedListInterface> getPagingListFirst_Progress(Member member, FundingState status, Pageable page);

    @Query("select i.id, i.fundingItemImg, i.fundingName, i.totalPrice, i.startDate, i.finishDate, i.status, m.fundingDate, m.payment_status, m.merchantId from FundingMember m join m.fundingItem i where id < :id and m.member = :member and i.status = :status order by m.id desc")
    List<ParticipatedListInterface> getPagingListAfter_Progress(Long id, Member member, FundingState status, Pageable page);

    //status = SUCCESS || FAIL
    @Query("select i.id, i.fundingItemImg, i.fundingName, i.totalPrice, i.startDate, i.finishDate, i.status, m.fundingDate, m.payment_status, m.merchantId from FundingMember m join m.fundingItem i where m.member = :member and i.status <> :status order by m.id desc")
    List<ParticipatedListInterface> getPagingListFirst_Finished(Member member, FundingState status, Pageable page);

    @Query("select i.id, i.fundingItemImg, i.fundingName, i.totalPrice, i.startDate, i.finishDate, i.status, m.fundingDate, m.payment_status, m.merchantId from FundingMember m join m.fundingItem i where id < :id and m.member = :member and i.status <> :status order by m.id desc")
    List<ParticipatedListInterface> getPagingListAfter_Finished(Long id, Member member, FundingState status, Pageable page);
}
