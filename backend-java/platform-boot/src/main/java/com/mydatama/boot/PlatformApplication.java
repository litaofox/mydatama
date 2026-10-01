package com.mydatama.boot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.mydatama")
@MapperScan({"com.mydatama.iam.mapper", "com.mydatama.gov.mapper", "com.mydatama.ds.mapper", "com.mydatama.product.mapper"})
public class PlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(PlatformApplication.class, args);
    }
}
