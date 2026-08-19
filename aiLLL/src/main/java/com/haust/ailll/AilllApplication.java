package com.haust.ailll;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.haust.ailll.mapper")
public class AilllApplication {

    public static void main(String[] args) {
        SpringApplication.run(AilllApplication.class, args);
    }

}