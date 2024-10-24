package com.liyh.VO;

import lombok.Data;

@Data
//@Schema(description = "文件信息")
public class FileVO {
   // @Schema(description = "文件url")
    private String url;

   // @Schema(description = "文件名称")
    private String fileName;

   // @Schema(description = "文件大小")
    private Long size;

    //@Schema(description = "文件扩展名")
    private String extname;
}