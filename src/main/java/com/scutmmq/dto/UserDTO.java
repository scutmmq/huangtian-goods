package com.scutmmq.dto;

import lombok.Data;

@Data
public class UserDTO {
    private Long id;
    private String nickName;
    private String token;
    private String image;
    private  String username;

    /**
     * 用户角色:USER / MERCHANT / ADMIN,默认 USER。配合 AI 工具权限拦截。
     * B2 引入;当前 LoginInterceptor 未填充,默认 USER。后续接入商家账号体系时覆盖。
     */
    private String role;
}
