package com.scutmmq.interceptor;

import com.scutmmq.dto.UserDTO;
import com.scutmmq.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class LoginCertificationInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserDTO userDTO = UserHolder.getUser();
        if(userDTO==null){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return  false;
        }
        return true;
    }
}
