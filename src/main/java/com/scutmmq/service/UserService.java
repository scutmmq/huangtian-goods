package com.scutmmq.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.scutmmq.dto.LoginDTO;
import com.scutmmq.dto.PasswordDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.User;

public interface UserService extends IService<User> {
    Result login(LoginDTO loginDTO);

    Result logout();

    Result register(User user);

    Result getUser();

    Result updateUser(User user);

    Result updatePassword(PasswordDTO passwordDTO);

    Result sign();

    Result getSignTotal(String year, String month);

}
