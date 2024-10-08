package com.liyh.VO;

import lombok.Data;
import lombok.ToString;

import java.io.Serializable;

@Data
@ToString
public class NotifyMsgSendVO implements Serializable {

    private static final long serialVersionUID = 5905249092659173678L;

    private String priKey;

    private String businessType;

    private String fileMD5;

    private String originalFilename;


}
