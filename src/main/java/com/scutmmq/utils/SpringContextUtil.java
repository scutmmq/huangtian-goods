package com.scutmmq.utils;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * Spring上下文工具类，安全获取代理对象
 */
@Component
public class SpringContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext context) throws BeansException {
        applicationContext = context;
    }

    // 根据接口类型获取代理对象（关键：注入接口，不是实现类）
    public static <T> T getBean(Class<T> clazz) {
        return applicationContext.getBean(clazz);
    }
}