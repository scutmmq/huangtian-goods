package com.scutmmq;

import com.scutmmq.utils.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;


public class JwtTokenTest {

    @Test
    public  void  testJwtToken() throws InterruptedException {
        Map<String,Object> claims = new HashMap<>();
        claims.put("id",1);
        claims.put("nickName","荒天帝");
        claims.put("images","/images/1.png");
        final String token = JwtUtils.generateJwtToken(claims);
        System.out.println(token);

        final Claims claims1 = JwtUtils.parseJwtToken(token);
        System.out.println(claims1);

        System.out.println("id1: " + claims1.get("id",Integer.class));
        System.out.println("nickName1: " + claims1.get("nickName",String.class));


        try {
            final Claims claims2 = JwtUtils.parseJwtToken("eyJhbGciOiJIUzI1NiJ9.eyJuaWNrTmFtZSI6IuiNkuWkqeW4nSIsImlkIjoiMiIsImV4cCI6MTc2Mjc1MjIyMH0.xZH6bjLTfeWuELQQaUlEf1Y3I3yoiURIm_sM3B69h8M");
            System.out.println("id2: " + claims2.get("id",Integer.class));
            System.out.println("nickName2: " + claims2.get("nickName",String.class));
        }catch (Exception e){
            if (e instanceof ExpiredJwtException) {
                System.out.println("令牌过期!!!");
            }else if(e instanceof NullPointerException){
                System.out.println("未登录");
            }
        }
    }
}
