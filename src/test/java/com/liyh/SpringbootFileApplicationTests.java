package com.liyh;

import com.liyh.VO.FileVO;
import com.liyh.VO.NotifyMsgSendVO;
import com.liyh.config.MinioConfig;
import com.liyh.mq.producer.NotifyMsgProducer;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.errors.ServerException;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Resource;

@SpringBootTest
class SpringbootFileApplicationTests {
    @Autowired
    private RedisTemplate<String,Object> redisTemplate;
    @Resource
    private NotifyMsgProducer notifyMsgProducer;
    @Autowired
    private MinioClient client;
    @Autowired
    private MinioConfig minioConfig;
    @Test
    void contextLoads() {
        // 设置键值对
        redisTemplate.opsForValue().set("key", "value");


        String value = (String) redisTemplate.opsForValue().get("key");
        System.out.println(value);
    }

    @Test
    void produce() {
        NotifyMsgSendVO vo = new NotifyMsgSendVO();
        vo.setPriKey(UUID.randomUUID().toString());
        vo.setFileMD5("3sfsfdsfefsfsfs");
        vo.setBusinessType("msg_send");
        notifyMsgProducer.send(vo);
        return;
    }
    @Test
    void clearRedis(){
        Set<String> keys = redisTemplate.keys("*");
        redisTemplate.delete(keys);
    }

    @Test
    public void minioUpload() throws Exception {
        File file1 = new File("D:\\Java\\springboot-file\\test.mp4");
        MultipartFile file = getMultipartFile(file1);
        FileVO fileVO = new FileVO();
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
            System.out.println("文件上传成功！");
            // 组装文件信息，返回前端 或者存入数据路
            String url = minioConfig.getUrl() + "/" + minioConfig.getBucketName() + "/" + originalFilename;
            fileVO.setUrl(url);
            fileVO.setSize(file.getSize());
            fileVO.setFileName(originalFilename);
            fileVO.setExtname(extname);

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
