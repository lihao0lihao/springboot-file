package com.liyh.mq.producer;

import com.alibaba.fastjson.JSON;
import com.liyh.VO.NotifyMsgSendVO;
import com.liyh.constant.ExchangeName;
import com.liyh.constant.RoutingKeyName;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
public class NotifyMsgProducer {
    @Resource
    private RabbitTemplate rabbitTemplate;
    private AtomicInteger count = new AtomicInteger(0);

    public void send(NotifyMsgSendVO notifyMsgSendVO) {
        log.debug("生产消息【{}】",notifyMsgSendVO);

        rabbitTemplate.setConfirmCallback(new RabbitTemplate.ConfirmCallback() {
            /**
             *
             * @param correlationData 相关配置信息
             * @param ack 交换机是否成功收到消息
             * @param cause 错误信息
             */
            @Override
            public void confirm(CorrelationData correlationData, boolean ack, String cause) {
                if (ack){
                    log.info("消息成功发送");
                }else {
                    log.info("消息发送失败");
                    log.info("错误原因"+cause);
                }
            }});
        Message message= MessageBuilder.withBody(JSON.toJSONString(notifyMsgSendVO).getBytes(StandardCharsets.UTF_8))
                .setMessageId(String.valueOf(count.getAndAdd(1))).build();

        this.rabbitTemplate.convertAndSend(ExchangeName.NOTIFY_MSG.name(),
                RoutingKeyName.NOTIFY_MSG.name(), message);
    }
}
