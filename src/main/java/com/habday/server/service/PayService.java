package com.habday.server.service;

import com.google.gson.Gson;
import com.habday.server.domain.fundingItem.FundingItem;
import com.habday.server.domain.fundingItem.FundingItemRepository;
import com.habday.server.domain.fundingMember.FundingMember;
import com.habday.server.domain.fundingMember.FundingMemberRepository;
import com.habday.server.domain.member.Member;
import com.habday.server.domain.member.MemberRepository;
import com.habday.server.domain.payment.Payment;
import com.habday.server.domain.payment.PaymentRepository;
import com.habday.server.dto.req.iamport.NoneAuthPayBillingKeyRequest;
import com.habday.server.dto.req.iamport.NoneAuthPayScheduleRequestDto;
import com.habday.server.dto.req.iamport.NoneAuthPayUnscheduleRequestDto;
import com.habday.server.dto.res.iamport.GetBillingKeyResponseDto;
import com.habday.server.dto.res.iamport.GetPaymentListsResponseDto.PaymentList;
import com.habday.server.dto.res.iamport.GetPaymentListsResponseDto;
import com.habday.server.dto.res.iamport.UnscheduleResponseDto;
import com.habday.server.exception.CustomException;
import com.habday.server.exception.CustomExceptionWithMessage;
import com.siot.IamportRestClient.IamportClient;
import com.siot.IamportRestClient.exception.IamportResponseException;
import com.siot.IamportRestClient.request.BillingCustomerData;
import com.siot.IamportRestClient.request.ScheduleData;
import com.siot.IamportRestClient.request.ScheduleEntry;
import com.siot.IamportRestClient.request.UnscheduleData;
import com.siot.IamportRestClient.response.BillingCustomer;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Schedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.habday.server.constants.ExceptionCode.*;
import static com.habday.server.constants.ScheduledPayState.cancel;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {
    private final PaymentRepository paymentRepository;
    private final MemberRepository memberRepository;
    private final FundingMemberRepository fundingMemberRepository;
    private final FundingItemRepository fundingItemRepository;
    private final IamportClient iamportClient =
            new IamportClient("3353771108105637", "CrjUGS59xKtdBK1eYdj7r4n5TnuEDGcQo12NLdRCetjCUCnMsDFk5Q9IqOlhhH7QELBdakQTIB5WfPcg");


    private String createCustomerUid(Long memberId){
        Long paymentNum = paymentRepository.countByMemberId(memberId)+1;
        return "cus_m" + memberId + "_p" + paymentNum;//ex) cus_m2_p2
    }

    public String createMerchantUid(Long fundingItemId, Long memberId){
        Long itemNum = fundingMemberRepository.countByFundingItemIdAndMemberId(fundingItemId, memberId) + 8;
        return "mer_f" + fundingItemId + "_m" + memberId + "_i" + itemNum;//특정 아이템에 멤버 참여 횟수 정하기 ex)mer_f1_m2_i2
    }
    @Transactional
    public GetBillingKeyResponseDto getBillingKey(NoneAuthPayBillingKeyRequest billingKeyRequest, Long memberId){
        String cardNumber = billingKeyRequest.getCard_number();
        Payment existingPayment = paymentRepository.findByCardNumberEnd(cardNumber.substring(15, 19));

        if (existingPayment != null)
            throw new CustomException(CARD_ALREADY_EXIST);

        String customer_uid = createCustomerUid(memberId);
        IamportResponse<BillingCustomer> iamportResponse = getBillingKeyFromIamport(billingKeyRequest, customer_uid);
        log.debug("iamportResponse: " + new Gson().toJson(iamportResponse));

        if(iamportResponse.getCode() != 0){
            throw new CustomExceptionWithMessage(GET_BILLING_KEY_FAIL, iamportResponse.getMessage());
        }

        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new CustomException(NO_MEMBER_ID));

        paymentRepository.save(Payment.builder()
                .paymentName(billingKeyRequest.getPayment_name())
                .billingKey(customer_uid)
                .member(member)
                .cardNumberEnd(cardNumber.substring(15, 19))
                .build());

        return GetBillingKeyResponseDto.of(billingKeyRequest.getPayment_name(), customer_uid, iamportResponse.getCode(), iamportResponse.getMessage());
    }

    private IamportResponse<BillingCustomer> getBillingKeyFromIamport(NoneAuthPayBillingKeyRequest billingKeyRequest, String customer_uid){
        BillingCustomerData billingCustomerData = new BillingCustomerData(
                customer_uid, billingKeyRequest.getCard_number(),
                billingKeyRequest.getExpiry(), billingKeyRequest.getBirth());

        billingCustomerData.setPwd2Digit(billingKeyRequest.getPwd_2digit());
        billingCustomerData.setPg("nice.nictest04m");

        try {
            return iamportClient.postBillingCustomer(customer_uid, billingCustomerData);
        } catch (IOException e) {
            throw new CustomException(BILLING_KEY_INTERNAL_ERROR);
        } catch (IamportResponseException e) {
            throw new CustomException(BILLING_KEY_INTERNAL_ERROR);
        }
    }

    public GetPaymentListsResponseDto getPaymentLists(Long memberId){
        List<PaymentList> paymentLists =  paymentRepository.findByMemberId(memberId);
        if (paymentLists == null){
            return GetPaymentListsResponseDto.of(null);//return 등록된 결제정보 없음
        }

        return GetPaymentListsResponseDto.of(paymentLists);
    }

    public IamportResponse<List<Schedule>> noneAuthPaySchedule(NoneAuthPayScheduleRequestDto scheduleRequestDto){
        ScheduleEntry scheduleEntry= new ScheduleEntry(
                scheduleRequestDto.getMerchant_uid(), scheduleRequestDto.getSchedule_at(), scheduleRequestDto.getAmount());
        scheduleEntry.setName(scheduleRequestDto.getName());
        scheduleEntry.setBuyerName(scheduleRequestDto.getBuyer_name());
        scheduleEntry.setBuyerTel(scheduleRequestDto.getBuyer_tel());
        scheduleEntry.setBuyerEmail(scheduleRequestDto.getBuyer_email());

        ScheduleData scheduleData = new ScheduleData(scheduleRequestDto.getCustomer_uid());
        scheduleData.addSchedule(scheduleEntry);
        try {
            return iamportClient.subscribeSchedule(scheduleData);
        } catch (IamportResponseException e) {
            throw new CustomException(PAY_SCHEDULING_INTERNAL_ERROR);
        } catch (IOException e) {
            throw new CustomException(PAY_SCHEDULING_INTERNAL_ERROR);
        }
    }

    private IamportResponse<List<Schedule>> unscheduleFromIamport(Long paymentId, String merchant_uid){
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new CustomException(NO_PAYMENT_EXIST));

        UnscheduleData unscheduleData = new UnscheduleData(payment.getBillingKey());//paymentId 주기
        unscheduleData.addMerchantUid(merchant_uid);//누락되면 빌링키에 관련된 모든 예약정보 일괄취소
        try {
            return iamportClient.unsubscribeSchedule(unscheduleData);
        } catch (IamportResponseException e) {
            throw new CustomException(PAY_UNSCHEDULING_INTERNAL_ERROR);
        } catch (IOException e) {
            throw new CustomException(PAY_UNSCHEDULING_INTERNAL_ERROR);
        }
    }

    public UnscheduleResponseDto noneAuthPayUnschedule(NoneAuthPayUnscheduleRequestDto unscheduleRequestDto, Long memberId){
        FundingMember fundingMember = fundingMemberRepository.findById(unscheduleRequestDto.getFundingMemberId())
                .orElseThrow(() -> new CustomException(NO_FUNDING_MEMBER_ID));
        FundingItem fundingItem = fundingItemRepository.findById(fundingMember.getFundingItem().getId())
                .orElseThrow(()-> new CustomException(NO_FUNDING_ITEM_ID));

        BigDecimal cancelableAmount = fundingMember.getAmount().subtract(fundingMember.getCancel_amount());

        if (cancelableAmount.compareTo(BigDecimal.ZERO) == 0) {//이미 환불 완료됨
            throw new CustomException(ALREADY_CANCELED);
        }

        IamportResponse<List<Schedule>> iamportResponse = unscheduleFromIamport(fundingMember.getPaymentId(), fundingMember.getMerchant_id());

        if(iamportResponse.getCode() != 0){
            throw new CustomExceptionWithMessage(PAY_SCHEDULING_INTERNAL_ERROR, iamportResponse.getMessage());
        }

        LocalDate cancelDate = LocalDate.now();
        fundingMember.updateCancel(fundingMember.getAmount(), unscheduleRequestDto.getReason(), cancel, cancelDate);

        return UnscheduleResponseDto.builder()
                .merchant_uid(fundingMember.getMerchant_id())
                .merchant_name(fundingItem.getFundingName())
                .cancelDate(cancelDate)
                .amount(fundingMember.getCancel_amount())
                .build();
    }
}
