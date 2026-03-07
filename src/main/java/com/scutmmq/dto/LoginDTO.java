package com.scutmmq.dto;

import com.scutmmq.enums.LoginType;
import lombok.Data;

@Data
public class LoginDTO {
    private String login;
    private String password;
    private LoginType loginType;
}
