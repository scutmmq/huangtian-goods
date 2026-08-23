package com.scutmmq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@MapperScan({"com.scutmmq.mapper", "com.scutmmq.ai.mapper"})
@ConfigurationPropertiesScan({"com.scutmmq", "com.scutmmq.ai"})
public class OnlineMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineMallApplication.class, args);
    }

}
