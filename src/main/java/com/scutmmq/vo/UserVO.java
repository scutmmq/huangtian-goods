package com.scutmmq.vo;

import com.scutmmq.enums.UserGender;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private Long id;
    private String username;
    private UserGender gender;
    @DateTimeFormat(pattern = "yyyy-mm-dd")
    private LocalDate birthday;
    private String nickName;
    private String phone;
    private String image;
    private String address;
    private LocalDateTime lastLogin;
    private String email;
}
