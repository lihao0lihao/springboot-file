package com.liyh.mq.consumer;

import com.alibaba.fastjson.JSON;
import com.liyh.VO.FileVO;
import com.liyh.VO.NotifyMsgSendVO;

import com.liyh.config.MinioConfig;
import com.liyh.constant.QueueNames;
import com.rabbitmq.client.Channel;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ServerException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
@Slf4j
public class NotifyMsgConsumer {
    @Autowired
    private MinioConfig minioConfig;

    // 注入minio client
    @Autowired
    private MinioClient client;

    @Resource
    private RedisTemplate redisTemplate;
    public static final ConcurrentMap<String, Integer> map = new ConcurrentHashMap<>();
    @RabbitListener(queues = QueueNames.NOTIFY_MSG_QUEUE)
    public void msgSend(Channel channel, Message message) throws IOException, InterruptedException {
        String messageId=message.getMessageProperties().getMessageId();
        byte[] bytesBody=message.getBody();
        NotifyMsgSendVO vo= JSON.parseObject(new String(bytesBody, StandardCharsets.UTF_8),NotifyMsgSendVO.class);
        String md5Value = vo.getFileMD5();
        String originalFilename = vo.getOriginalFilename();
        System.out.println("消费者收到消息:"+vo);
        try {

            if (!redisTemplate.hasKey(md5Value)) {
                System.out.println(md5Value + "不存在！");
            }

            Integer file_chunks = Integer.valueOf(redisTemplate.opsForHash().get(md5Value, "file_chunks").toString());
            String userDir = System.getProperty("user.dir");
            File writeFile = new File(userDir + File.separator + originalFilename);
            OutputStream outputStream = new FileOutputStream(writeFile);
            InputStream inputStream = null;
            for (int i = 0; i < file_chunks; i++) {
                String tmpPath = redisTemplate.opsForHash().get(md5Value, "chunk_location_" + i).toString();
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
            channel.basicAck(message.getMessageProperties().getDeliveryTag(), false);
            MultipartFile cMultiFile = getMultipartFile(writeFile);
            FileVO fileVO = minioUpload(cMultiFile);
        }catch (Exception e) {
            this.delTmpFile(md5Value);
            map.put(messageId,map.getOrDefault(messageId, 0)+1);

            log.error("接收消息失败");
            log.info("开始重试");
            log.info(messageId);

            //重复处理失败
            if(map.get(messageId)<=3) {
                System.out.println("确认失败,重新入队");
                log.info("确认失败,重新入队");
                channel.basicNack(message.getMessageProperties().getDeliveryTag(),false, true);
                map.put(messageId, map.getOrDefault(messageId, 0)+1);

            }
            else {

                log.info("重试仍然失败,所以我们决定丢弃这个消息");
                this.delTmpFile(md5Value);
                channel.basicReject(message.getMessageProperties().getDeliveryTag(),false);
            }

        }
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
    private FileVO minioUpload(MultipartFile file){
        FileVO fileVO = new FileVO();
        try {
            // 获取文件真实名称
            String originalFilename = file.getOriginalFilename();
            // 获取文件的扩展名 例如.jpg .doc
            String extname = originalFilename.substring(originalFilename.lastIndexOf("."));
            // 构建文件上传相关信息
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(originalFilename)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType("application/octet-stream")
                    .build();
            // 将文件上传到minio服务器
            client.putObject(args);
            log.info("文件上传成功");
            // 组装文件信息，返回前端 或者存入数据路
            String url = minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/" + originalFilename;
            fileVO.setUrl(url);
            fileVO.setSize(file.getSize());
            fileVO.setFileName(originalFilename);
            fileVO.setExtname(extname);
        } catch (Exception e) {
            try {
                throw new ServerException("文件上传异常" + e.getCause().toString(),0,"sss");
            } catch (ServerException serverException) {
                serverException.printStackTrace();
            }
        }
        return fileVO;
    }
    public static MultipartFile getMultipartFile(File file) {
        FileItem item = new DiskFileItemFactory().createItem("file"
                , MediaType.MULTIPART_FORM_DATA_VALUE
                , true
                , file.getName());
        try (InputStream input = new FileInputStream(file);
             OutputStream os = item.getOutputStream()) {
            // 流转移
            IOUtils.copy(input, os);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid file: " + e, e);
        }

        return new CommonsMultipartFile(item);
    }

}
