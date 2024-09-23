package com.liyh.controller;


import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.util.*;
import javax.annotation.Resource;

@RestController
@RequestMapping("/file")
@Slf4j
public class File1Controller {
    @Resource
    private RedisTemplate redisTemplate;
    /**
     * 检验分片文件是否已经上传过
     *
     * @param fileMd5  整体文件md5值
     * @param chunk    当前上传分片在所有分片文件中索引位置
     * @param chunkMd5 分片文件的md5值
     * @return
     */

    @PostMapping("/check")
    public boolean check(String fileMd5, String chunk, String chunkMd5) {
        Object o = redisTemplate.opsForHash().get(fileMd5, "chunk_md5_" + chunk);
        if (chunkMd5.equals(o)) {
            return true;
        }
        System.out.println(fileMd5+chunk+chunkMd5);
        return false;
    }

    /**
     * 分片上传接口
     *
     * @param request
     * @param multipartFile
     * @return
     * @throws IOException
     */
    @PostMapping("/upload")
    public String upload(HttpServletRequest request, MultipartFile multipartFile) {
        log.info("分片上传....");
        Map<String, String> requestParam = this.doRequestParam(request);
        String md5Value = requestParam.get("md5Value");//整体文件的md5值
        String chunkIndex = requestParam.get("chunk");//分片在所有分片文件中索引位置
        String start = requestParam.get("start");//当前分片在整个数据文件中的开始位置
        String end = requestParam.get("end");//当前分片在整个数据文件中的结束位置
        String chunks = requestParam.get("chunks");//整体文件总共被分了多少片
        String fileSize = requestParam.get("size");//整体文件大小
        String chunkMd5 = requestParam.get("chunkMd5");//分片文件的md5值
        String userDir = System.getProperty("user.dir");
        String chunkFilePath = userDir + File.separator + chunkMd5 + "_" + chunkIndex;
        File file = new File(chunkFilePath);
        try {
            multipartFile.transferTo(file);
            Map<String, String> map = new HashMap<>();
            map.put("chunk_location_" + chunkIndex, chunkFilePath);//分片存储路径
            map.put("chunk_start_end_" + chunkIndex, start + "_" + end);
            map.put("file_size", fileSize);
            map.put("file_chunks", chunks);
            map.put("chunk_md5_" + chunkIndex, chunkMd5);
            redisTemplate.opsForHash().putAll(md5Value, map);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (IllegalStateException e) {
            e.printStackTrace();
        }

        return "success";
    }

    /**
     * 合并分片文件接口
     *
     * @param request
     * @return
     * @throws IOException
     */
    @PostMapping("/merge")
    public String merge(HttpServletRequest request) throws IOException, InterruptedException {
        log.info("合并分片...");
        Map<String, String> requestParam = this.doRequestParam(request);
        String md5Value = requestParam.get("md5Value");
        String originalFilename = requestParam.get("originalFilename");
        //校验切片是否己经上传完毕
        boolean flag = this.checkBeforeMerge(md5Value);
        if (!flag) {
            return "切片未完全上传";
        }
//        检查是否已经有相同md5值的文件上传；主要是对名字不同，而实际文件相同的文件，直接对原文件进行复制；
        Object file_location = redisTemplate.opsForHash().get(md5Value, "file_location");
        if (file_location != null) {
            String source = file_location.toString();
            File file = new File(source);
            if (!file.getName().equals(originalFilename)) {
                File target = new File(System.getProperty("user.dir") + File.separator + originalFilename);
                Files.copy(file.toPath(), target.toPath());
                return "success";
            }

        }
        //这里要特别注意，合并分片的时候一定要按照分片的索引顺序进行合并，否则文件无法使用；
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
        return "success";
    }

    @GetMapping("/download")
    public String download(String fileName, HttpServletResponse response) throws IOException {
        response.setContentType("application/octet-stream");
        response.setHeader("Content-Disposition", "attachment; filename=" + URLEncoder.encode(fileName, "UTF-8"));
        String userDir = System.getProperty("user.dir");
        File file = new File(userDir + File.separator + fileName);
        InputStream inputStream = new FileInputStream(file);
        byte[] bytes = new byte[1024 * 1024];
        ServletOutputStream outputStream = response.getOutputStream();
        while (inputStream.read(bytes) != -1) {
            outputStream.write(bytes);
        }
        inputStream.close();
        outputStream.close();
        return "success";
    }


    private void delTmpFile(String md5Value) throws JsonProcessingException {
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

    private Map<String, String> doRequestParam(HttpServletRequest request) {
        Map<String, String> requestParam = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            String paramValue = request.getParameter(paramName);
            requestParam.put(paramName, paramValue);
            log.info(paramName + "：" + paramValue);
        }
        log.info("----------------------------");
        return requestParam;
    }

    /**
     * 合并分片前检验文件整体的所有分片是否全部上传
     *
     * @param key
     * @return
     */
    private boolean checkBeforeMerge(String key) {
        Map map = redisTemplate.opsForHash().entries(key);
        Object file_chunks = map.get("file_chunks");
        int i = 0;
        for (Object hashKey : map.keySet()) {
            if (hashKey.toString().startsWith("chunk_md5_")) {
                ++i;
            }
        }
        if (Integer.valueOf(file_chunks.toString())==(i)) {
            return true;
        }
        return false;
    }
}