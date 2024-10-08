package com.liyh.mq.consumer;

import com.liyh.VO.NotifyMsgSendVO;

import com.liyh.constant.QueueNames;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class NotifyMsgConsumer {
    @Resource
    private RedisTemplate redisTemplate;

    @RabbitListener(queues = QueueNames.NOTIFY_MSG_QUEUE)
    public void msgSend(NotifyMsgSendVO vo) throws IOException, InterruptedException {
        System.out.println("消费者收到消息:"+vo);
        String md5Value=vo.getFileMD5();
        String originalFilename=vo.getOriginalFilename();
        if(!redisTemplate.hasKey(md5Value)){
            System.out.println(md5Value+"不存在！");
        }

        Integer file_chunks = Integer.valueOf(redisTemplate.opsForHash().get(md5Value, "file_chunks").toString());
        String userDir = System.getProperty("user.dir");
        File writeFile = new File(userDir + File.separator + originalFilename);
        OutputStream outputStream = new FileOutputStream(writeFile);
        InputStream inputStream = null;
        for (int i = 0; i < file_chunks; i++) {
            String tmpPath = redisTemplate.opsForHash().get(md5Value,"chunk_location_" + i).toString();
            File readFile = new File(tmpPath);
            while (!readFile.exists()) {
                // 不存在休眠100毫秒后在重新判断
                Thread.sleep(100);
            }
            inputStream = new FileInputStream(readFile);
            byte[] bytes = new byte[1024 * 1024];
            while ((inputStream.read(bytes) != -1)) {
                outputStream.write(bytes);
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
        if (outputStream != null) {
            outputStream.close();
        }
        redisTemplate.opsForHash().put(md5Value, "file_location", userDir + File.separator + originalFilename);
        this.delTmpFile(md5Value);

    }

    private void delTmpFile(String md5Value) {
        Map map = redisTemplate.opsForHash().entries(md5Value);
        List<String> list = new ArrayList<>();
        for (Object hashKey : map.keySet()) {
            if (hashKey.toString().startsWith("chunk_location")) {
                String filePath = map.get(hashKey).toString();
                File file = new File(filePath);
                boolean flag = file.delete();
                list.add(hashKey.toString());
                log.info("delete:" + filePath + ",:" + flag);
            }
            if (hashKey.toString().startsWith("chunk_start_end_")) {
                list.add(hashKey.toString());
            }
            if (hashKey.toString().startsWith("chunk_md5_")) {
                list.add(hashKey.toString());
            }
        }
        list.add("file_chunks");
        list.add("file_size");
        redisTemplate.opsForHash().delete(md5Value, list.toArray());
    }
}
