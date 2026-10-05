package com.lhjypt.core.model.strategy;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 策略源码加密表（P1）——对应 strategy_crypto，密文存储、明文不入库。
 */
@Data
@TableName("strategy_crypto")
public class StrategyCrypto {

    public static final String ALGO_AES_GCM = "AES_256_GCM";

    @TableId(type = IdType.AUTO)
    private Long id;

    private String strategyId;

    private Integer version;

    private String algo;

    /** 密文 Base64（IV ‖ ciphertext ‖ tag） */
    private String ciphertext;

    private String keyId;

    /** 源码 SHA-256，防篡改 */
    private String checksum;

    private String uploadedBy;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
