package com.scutmmq;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.scutmmq.mapper")
public class OnlineMallApplication {

    public static void main(String[] args) {
        SpringApplication.run(OnlineMallApplication.class, args);
    }

}
