package com.liyh.mq.producer;

import com.liyh.VO.NotifyMsgSendVO;
import com.liyh.constant.ExchangeName;
import com.liyh.constant.RoutingKeyName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
public class NotifyMsgProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;


    public void send(NotifyMsgSendVO notifyMsgSendVO) {
        log.debug("生产消息【{}】",notifyMsgSendVO);
        this.rabbitTemplate.convertAndSend(ExchangeName.NOTIFY_MSG.name(),
                RoutingKeyName.NOTIFY_MSG.name(), notifyMsgSendVO);
    }
}
