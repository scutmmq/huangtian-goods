package com.scutmmq.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.exception.AuthorizeException;
import com.scutmmq.utils.JwtUtils;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.UserHolder;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

import  static  com.scutmmq.utils.RedisConstants.*;

@Component
@Slf4j
@RequiredArgsConstructor
public class RefreshInterceptor implements HandlerInterceptor {

    private  final  StringRedisTemplate stringRedisTemplate;

    private final RedissonClient redissonClient;

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        // 获取 token
        String token = request.getHeader("Authorization");

        if(token==null||token.isEmpty()){
            return  true;
        }

        // 处理前缀：若包含 "Bearer "，则截取后面的令牌部分
        if (token.startsWith("Bearer ")) {
            token = token.substring(7).trim(); // 去除前缀并修剪空格
        }


        try {
            // 获取jwt令牌
            Claims claims = JwtUtils.parseJwtToken(token);
            UserDTO userDTO = BeanUtil.fillBeanWithMap(claims,new UserDTO(),true);

            // 令牌通过，判断redis是否存在令牌
            final Boolean hasKey = stringRedisTemplate.hasKey(TOKEN_KEY + token);

            if(!hasKey){
                // 不存在，不更新令牌，不存threadLocal
                return  true;
            }
            // 计算令牌过期时间
            long remainMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            Long redisExpire = stringRedisTemplate.getExpire(TOKEN_KEY + token, TimeUnit.MILLISECONDS);

            // 剩余有效时间小于 20 分钟时触发滑动续期
            if (remainMillis > 0 && remainMillis < 20 * 60 * 1000L && redisExpire != null && redisExpire > 0) {
                // 刷新令牌
                final String newToken = JwtUtils.generateJwtToken(claims);
                userDTO.setToken(newToken);
                // 存用户进入threadLocal
                UserHolder.saveUser(userDTO);
                // 返回新token给用户
                response.setHeader("Authorization", newToken);

                // 在redis中添加新token，保持有状态的jwt令牌
                stringRedisTemplate
                        .opsForValue()
                        .set(TOKEN_KEY + newToken, USER_PERMISSION, TOKEN_EXPIRATION, TOKEN_TIME_UNIT);
                // 不立即删除旧token，设置15秒平滑过渡
                stringRedisTemplate.expire(TOKEN_KEY + token, 15, TimeUnit.SECONDS);
                return true;
            }
            userDTO.setToken(token);
            UserHolder.saveUser(userDTO);
            return true;

        } catch (ExpiredJwtException e) {
            log.warn("令牌已过期:{}", e.getMessage());
            throw new AuthorizeException("登录已过期，请重新登录");
        } catch (Exception e) {
            log.error("令牌无效:{}", e.getMessage());
            throw new AuthorizeException("登录凭据无效");
        }
    }


}
