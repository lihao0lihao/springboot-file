package com.liyh;

import com.liyh.VO.NotifyMsgSendVO;
import com.liyh.mq.producer.NotifyMsgProducer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import java.util.UUID;
import javax.annotation.Resource;

@SpringBootTest
class SpringbootFileApplicationTests {
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private NotifyMsgProducer notifyMsgProducer;
    @Test
    void contextLoads() {
        // 设置键值对
        redisTemplate.opsForValue().set("key", "value");


        String value = (String) redisTemplate.opsForValue().get("key");
        System.out.println(value);
    }

//    @Test
//    void produce() {
//        NotifyMsgSendVO vo = new NotifyMsgSendVO();
//        vo.setPriKey(UUID.randomUUID().toString());
//        vo.setFileMD5("3sfsfdsfefsfsfs");
//        vo.setBusinessType("msg_send");
//        notifyMsgProducer.send(vo);
//        return;
//    }

}
