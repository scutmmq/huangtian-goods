package com.scutmmq.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.PushBuilder;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

public class JwtUtils {
    private static final String SECRET = "woainizhongguoqinaidemuqinwoweiniliulei";
    private  static  final SecretKey SIGNINGKEY = Keys.hmacShaKeyFor(SECRET.getBytes());
    private  static final  long EXPIRATION_TIME =3600 * 1000;

    public static String generateJwtToken(Map<String,Object>claims){
        return Jwts.builder()
                .signWith(SIGNINGKEY)
                .claims(claims) //自定义数据
                .expiration(new Date(System.currentTimeMillis()+EXPIRATION_TIME))
                .compact();
    }

    public static Claims parseJwtToken(String token){
        return Jwts
                .parser()
                .verifyWith(SIGNINGKEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
