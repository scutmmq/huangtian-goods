package com.scutmmq.security;

import com.scutmmq.exception.AuthorizeException;
import com.scutmmq.interceptor.RefreshInterceptor;
import com.scutmmq.utils.JwtUtils;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.UserHolder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class JwtInterceptorSecurityTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private RefreshInterceptor refreshInterceptor;

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void testPreHandleAllowsEmptyTokenForPublicRoutes() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean result = refreshInterceptor.preHandle(request, response, new Object());
        assertTrue(result);
        assertNull(UserHolder.getUser());
    }

    @Test
    void testPreHandleThrowsExceptionOnExpiredJwtToken() {
        // 创建一个已过期的 token
        SecretKey key = Keys.hmacShaKeyFor("woainizhongguoqinaidemuqinwoweiniliulei".getBytes());
        String expiredToken = Jwts.builder()
                .signWith(key)
                .claim("id", 1L)
                .expiration(new Date(System.currentTimeMillis() - 10000)) // 10秒前已过期
                .compact();

        when(request.getHeader("Authorization")).thenReturn(expiredToken);

        AuthorizeException ex = assertThrows(AuthorizeException.class, () ->
                refreshInterceptor.preHandle(request, response, new Object())
        );
        assertEquals("登录已过期，请重新登录", ex.getMessage());
    }

    @Test
    void testPreHandleThrowsExceptionOnMalformedToken() {
        when(request.getHeader("Authorization")).thenReturn("not.a.valid.jwt.token");

        AuthorizeException ex = assertThrows(AuthorizeException.class, () ->
                refreshInterceptor.preHandle(request, response, new Object())
        );
        assertEquals("登录凭据无效", ex.getMessage());
    }

    @Test
    void testPreHandleValidTokenSavesUserHolder() throws Exception {
        Map<String, Object> claims = new HashMap<>();
        claims.put("id", 123L);
        claims.put("username", "testuser");
        claims.put("nickName", "测试用户");
        String token = JwtUtils.generateJwtToken(claims);

        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(stringRedisTemplate.hasKey(RedisConstants.TOKEN_KEY + token)).thenReturn(true);
        when(stringRedisTemplate.getExpire(RedisConstants.TOKEN_KEY + token, TimeUnit.MILLISECONDS))
                .thenReturn(30 * 60 * 1000L); // 剩余30分钟

        boolean result = refreshInterceptor.preHandle(request, response, new Object());
        assertTrue(result);
        assertNotNull(UserHolder.getUser());
        assertEquals(123L, UserHolder.getUser().getId());
    }
}
