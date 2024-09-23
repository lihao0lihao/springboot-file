package com.liyh.controller;

import com.liyh.service.FileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 文件上传测试接口
 *
 * @author liyh
 */
@RestController
@RequestMapping("/file1")
public class FileController {

    @Autowired
    private FileService fileService;

    /**
     * 单个文件上传，支持断点续传
     */
    @PostMapping("/upload")
    public void upload(HttpServletRequest request, HttpServletResponse response) {
        try {
            fileService.upload(request, response);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 普通文件下载
     */
    @GetMapping("/download")
    public void download(HttpServletRequest request, HttpServletResponse response) throws IOException {
        fileService.download(request, response);
    }

    /**
     * 分片文件下载
     */
    @GetMapping("/downloads")
    public String downloads() throws IOException {
        fileService.downloads();
        return "下载成功";
    }

}
