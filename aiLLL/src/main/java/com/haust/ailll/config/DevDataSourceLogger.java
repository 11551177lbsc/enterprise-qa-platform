package com.haust.ailll.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Dev 环境启动诊断 — 仅在 spring.profiles.active=dev 时激活。
 * 启动成功后打印当前生效的数据源配置，用于排查数据库连接问题。
 * 不会打印密码。
 */
@Component
@Profile("dev")
public class DevDataSourceLogger {

    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    @Value("${spring.datasource.username}")
    private String datasourceUsername;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @EventListener(ApplicationReadyEvent.class)
    public void logDatasourceConfig() {
        System.out.println("==================================================");
        System.out.println("[DEV] Dev 环境启动诊断 — 当前生效的数据源配置:");
        System.out.println("[DEV]   spring.datasource.url            = " + datasourceUrl);
        System.out.println("[DEV]   spring.datasource.username       = " + datasourceUsername);
        System.out.println("[DEV]   spring.datasource.driver-class   = " + driverClassName);
        System.out.println("[DEV]   (密码已隐藏，不在此处打印)");
        System.out.println("==================================================");
    }
}
