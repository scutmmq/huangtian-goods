package com.scutmmq.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.scutmmq.enums.LoginType;
import com.scutmmq.enums.UserGender;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

/**
 * 用户实体类
 * 对应表：tb_user
 * @author MMQ
 */
@Data
@TableName(value = "user",autoResultMap = true)
public class User {

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 账号
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 性别: 0-女, 1-男, 2-其他
     */
    private UserGender gender;

    /**
     * 生日
     */
    @DateTimeFormat(pattern = "yyyy-mm-dd")
    private LocalDate birthday;

    /**
     * 昵称
     */
    private String nickName;

    /**
     * 电话号码
     */
    private String phone;

    /**
     * 头像URL
     */
    private String image;

    /**
     * 地址
     */
    private String address;

    /**
     * 是否激活: 0-禁用, 1-激活
     */
    private Integer isActive;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLogin;

    /**
     * 创建时间
     */
    private LocalDateTime createdTime;

    /**
     * 修改时间
     */
    private LocalDateTime updatedTime;

}
