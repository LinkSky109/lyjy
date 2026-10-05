package com.lhjypt.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 量化交易平台核心服务启动类
 * 覆盖：行情接入、交易执行、风控、资金管理
 */
@SpringBootApplication
@EnableScheduling
public class LhjyptCoreApplication {

    public static void main(String[] args) {
        SpringApplication.run(LhjyptCoreApplication.class, args);
        System.out.println("""
                ========================================
                 lhjypt-core 启动成功
                 企业级量化交易平台 v0.1.0
                 API: http://localhost:8081
                ========================================
                """);
    }
}
