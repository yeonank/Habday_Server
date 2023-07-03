package com.habday.server.service;

import com.google.gson.Gson;
import com.habday.server.classes.Common;
import com.habday.server.config.retrofit.RestInterface;
import com.habday.server.constants.FundingState;
import com.habday.server.domain.fundingItem.FundingItem;
import com.habday.server.domain.fundingMember.FundingMember;
import com.habday.server.dto.req.iamport.CallbackScheduleRequestDto;
import com.habday.server.dto.req.iamport.NoneAuthPayUnscheduleRequestDto;
import com.habday.server.dto.res.iamport.UnscheduleResponseDto;
import com.habday.server.exception.CustomException;
import com.habday.server.exception.CustomExceptionWithMessage;
import com.siot.IamportRestClient.response.IamportResponse;
import com.siot.IamportRestClient.response.Payment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import retrofit2.Call;
import retrofit2.Response;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.habday.server.constants.ExceptionCode.*;
import static com.habday.server.constants.ScheduledPayState.fail;
import static com.habday.server.constants.ScheduledPayState.paid;

@Slf4j
@RequiredArgsConstructor
@Service
public class FundingCloseService extends Common {
    private final IamportService iamportService;
    private final RestInterface restService;

    /*
     * 1. FundingItem status == PROGRESS 중 오늘 날짜랑 같은게 있는지 확인하기(fundingService.checkFundingFinishDate()
     * 2. 날짜가 같으면 성공 실패(목표 퍼센트 달성) 확인하기(fundingService.checkFundingGoalPercent())
     * 3. 퍼센트 달성 실패 시 예약 취소하기
     *   - fundingItem status fail로 업데이트
     *   - fundingMember status cancel로 업데이트
     *   - 펀딩 실패 메일 보내기
     * 4. 퍼센트 달성 성공 시 fundingItem status success로 업데이트
     *   - 펀딩 성공 메일 보내기
     * */
    @Transactional
    @Scheduled(cron = "0 16 1 * * *") // 매일 밤 0시 5분에 실행
    public void checkFundingState() {
        log.info("schedule 시작");
        List<FundingItem> overdatedFundings =  fundingItemRepository.findByStatusAndFinishDate(FundingState.PROGRESS, LocalDate.now());
        overdatedFundings.forEach(fundingItem -> {
            log.debug("오늘 마감 fundingItem: " + fundingItem.getId());
            checkFundingGoalPercent(fundingItem.getId());
        });
        log.info("schedule 끝");
    }
    @Transactional
    public void checkFundingGoalPercent(Long fundingItemId) {
        FundingItem fundingItem = fundingItemRepository.findById(fundingItemId).orElseThrow(
                () -> new CustomException(NO_FUNDING_ITEM_ID)
        );
        BigDecimal goalPercent = fundingItem.getGoalPrice().divide(fundingItem.getItemPrice(), BigDecimal.ROUND_DOWN); //최소목표퍼센트
        BigDecimal realPercent = fundingItem.getTotalPrice().divide(fundingItem.getItemPrice(), BigDecimal.ROUND_DOWN); // 실제달성퍼센트

        log.debug(fundingItem.getId() + "goalPercent^^ " + goalPercent);
       log.debug(fundingItem.getId() + "realPercent^^ " + realPercent);
        log.debug(fundingItem.getId() + "realPercent.compareTo(goalPercent)^^ : " + realPercent.compareTo(goalPercent));
        if (realPercent.compareTo(goalPercent) == 0 ||  realPercent.compareTo(goalPercent) == 1) { // 펀딩 최소 목표 퍼센트에 달성함
            fundingItem.updateFundingState(FundingState.SUCCESS);
            log.debug("최소 목표퍼센트 이상 달성함");
            //TODO 펀딩 성공 메일 보내기
        } else { // 펀딩 최소 목표 퍼센트에 달성 못함
            log.debug("최소 목표퍼센트 이상 달성 실패");
            fundingItem.updateFundingState(FundingState.FAIL);//update안됨
            List<FundingMember>  fundingMemberList = fundingMemberRepository.getMatchesWithFundingItem(fundingItem);
            fundingMemberList.forEach(fundingMember -> {
                NoneAuthPayUnscheduleRequestDto request = new NoneAuthPayUnscheduleRequestDto(fundingMember.getId(), "목표 달성 실패로 인한 결제 취소");
                Call<UnscheduleResponseDto> call = restService.unscheduleApi(request);//예약결제 취소 후 fundingMember status cancel로 업데이트
                try {
                    Response<UnscheduleResponseDto> response = call.execute();
                    log.debug("response: " + new Gson().toJson(response.body()));
                } catch (IOException e) {
                    throw new CustomException(FAIL_WHILE_UNSCHEDULING);
                }
                //TODO response로 펀딩 실패 메일 보내기
            });
            //throw new CustomException(FAIL_FINISH_FUNDING);
        }
    }

    // 펀딩 기간 만료 후, 펀딩 목표 퍼센트 달성 했는지 여부 확인 로직
    public void checkFundingFinishDate(FundingItem fundingItem){
        LocalDate now = LocalDate.now(); //현재 날짜 구하기
        System.out.println("now^^ " + now.isEqual(fundingItem.getFinishDate()));

        if (now.compareTo(fundingItem.getFinishDate()) == 0){
            checkFundingGoalPercent(fundingItem.getId());
        } else if(now.compareTo(fundingItem.getFinishDate()) < 0){
            log.debug("종료 이전");
            throw new CustomException(NOT_FINISH_FUNDING); // 펀딩이 아직 종료되지 않음
        }else{
            log.debug("종료 이후");
            throw new CustomException(ALREADY_FINISHED_FUNDING);
        }
    }

    /*
        <웹훅>
     * 1. 12시반  예약결제 실행
     * 2. 웹훅에서 fundingMember.payment_status paid로 update
     * 3. 결제 실패 시 실패 메일과 수동 결제 링크 보내기
     * 4. 결제 성공 시 결제 성공 메일 보내기
     * */
    public void callbackSchedule(CallbackScheduleRequestDto callbackRequestDto, HttpServletRequest request){
        String clientIp = getIp(request);
        String[] ips = {"52.78.100.19", "52.78.48.223", "52.78.5.241"};
        List<String> ipLists = new ArrayList<>(Arrays.asList(ips));

        FundingMember fundingMember = fundingMemberRepository.findByMerchantId(callbackRequestDto.getMerchant_uid());

        IamportResponse<Payment> response = iamportService.paymentByImpUid(callbackRequestDto.getImp_uid());

        if (!ipLists.contains(clientIp)){
            fundingMember.updateWebhookFail(fail, "ip주소가 맞지 않음");
            throw new CustomException(UNAUTHORIZED_IP);
        }

        if(!fundingMember.getAmount().equals(response.getResponse().getAmount())){
            fundingMember.updateWebhookFail(fail, "결제 금액이 맞지 않음");
            throw new CustomException(NO_CORRESPONDING_AMOUNT);
        }

        if(callbackRequestDto.getStatus() == paid.getMsg()){
            fundingMember.updateWebhookSuccess(paid);
        }else{
            fundingMember.updateWebhookFail(fail, response.getResponse().getFailReason());
            throw new CustomExceptionWithMessage(WEBHOOK_FAIL, response.getResponse().getFailReason());
        }
    }


    private String getIp(HttpServletRequest request) {
        String [] headers = {"X-Forwarded-For", "Proxy-Client-IP", "WL-Proxy-Client-IP", "HTTP_CLIENT_IP", "HTTP_X_FORWARDED_FOR", };

        String ip = request.getHeader("X-Forwarded-For");
        for(String header: headers){
            if (ip == null){
                ip = request.getHeader(header);
                log.info(">>>> " + header + " : " + ip);
            }
        }
        if (ip == null) {
            ip = request.getRemoteAddr();
        }

        return ip;
    }

}