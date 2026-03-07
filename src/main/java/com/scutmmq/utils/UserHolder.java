package com.scutmmq.utils;

import com.scutmmq.dto.UserDTO;
import lombok.Data;

@Data
public class UserHolder {

    private static ThreadLocal<UserDTO> threadLocal = new ThreadLocal<>();

    public static void saveUser(UserDTO userDTO){
        threadLocal.set(userDTO);
    }

    public static UserDTO getUser(){
        return threadLocal.get();
    }

    public static void removeUser(){
        threadLocal.remove();
    }

}
