package com.liyh.config;

import com.liyh.constant.ExchangeName;
import com.liyh.constant.QueueNames;
import com.liyh.constant.RoutingKeyName;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class QueueConfig {
    /**
     * 死信交换机
     */
    private final String DEAD_EXCHANGE = "deadExchange";
    /**
     * 死信队列
     */
    private final String DEAD_QUEUE = "deadQueue";
    /**
     * 死信路由key
     */
    private final String DEAD_ROUTTING_KEY = "deadKey";


    @Bean
    public DirectExchange defaultExchange() {
        return new DirectExchange(ExchangeName.DEFAULT.name());
    }

    @Bean
    public DirectExchange notifyMsgDirectExchange() {
        return new DirectExchange(ExchangeName.NOTIFY_MSG.name());
    }

    @Bean
    public Queue notifyMsgQueue() {
//        Map<String, Object> map = new HashMap<>();
//        map.put("x-dead-letter-exchange", DEAD_EXCHANGE);
//        map.put("x-dead-letter-routing-key", DEAD_ROUTTING_KEY);
//        map.put("x-message-ttl", 5000); // 设置队列消息5秒超时
//
//       // TopicExchange topicExchange = new TopicExchange(NORMAL_EXCHANGE);
//
//        // 消息队列重试次数设置
//        RetryTemplate retryTemplate = new RetryTemplate();
//        RetryPolicy retryPolicy = new SimpleRetryPolicy(5); // 重试5次
//        retryTemplate.setRetryPolicy(retryPolicy);
//

        return new Queue(QueueNames.NOTIFY_MSG_QUEUE, true);
    }


    @Bean
    public Binding notifyMsgQueueBinding() {
        return BindingBuilder
                .bind(notifyMsgQueue())
                .to(notifyMsgDirectExchange())
                .with(RoutingKeyName.NOTIFY_MSG);
    }


}
