package com.habday.server.domain.fundingItem;

import com.habday.server.constants.FundingState;
import com.habday.server.domain.member.Member;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.habday.server.dto.res.fund.GetHostingListResponseDto.HostingList;

import java.util.List;

public interface FundingItemRepository extends JpaRepository<FundingItem, Long> {
    //status = PROGRESS
    List<HostingList> findByStatusAndMemberOrderByIdDesc(FundingState status, Member member, Pageable page);
    List<HostingList> findByIdLessThanAndStatusAndMemberOrderByIdDesc(Long id, FundingState status, Member member, Pageable page);

    //status = SUCCESS || FAIL
    List<HostingList> findByStatusNotAndMemberOrderByIdDesc(FundingState status, Member member, Pageable page);
    List<HostingList> findByIdLessThanAndStatusNotAndMemberOrderByIdDesc(Long id, FundingState status, Member member, Pageable page);
    Boolean existsByIdLessThan(Long id);

    /*
    * select * from funding_item
    * where id < 이전id
    * and status = "PROGRESS"
    * and member_id = memberId
    * order by id desc
    * limit 10;
    * */
}