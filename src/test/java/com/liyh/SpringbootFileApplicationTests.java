package com.liyh;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

@SpringBootTest
class SpringbootFileApplicationTests {
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Test
    void contextLoads() {
        // 设置键值对
        redisTemplate.opsForValue().set("key", "value");


        String value = (String) redisTemplate.opsForValue().get("key");
        System.out.println(value);
    }

}
