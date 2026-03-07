package com.scutmmq.controller;

import com.scutmmq.dto.LoginDTO;
import com.scutmmq.dto.PasswordDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.User;
import com.scutmmq.service.UserService;
import com.scutmmq.service.NotificationService;
import com.scutmmq.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final NotificationService notificationService;

    /**
     * 注册功能
     * @param user 传入的注册信息
     * @return true
     */
    @PostMapping("/register")
    public Result addUser(@RequestBody User user){
        log.info("注册用户");
        return userService.register(user);
    }

    /**
     *  登录功能
     * @param loginDTO 登录表单数据
     * @return userDTO
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginDTO loginDTO){
        System.out.println(loginDTO);
        return userService.login(loginDTO);
    }

    /**
     *  退出登录
     * @return 无
     */
    @PostMapping("/logout")
    public Result logout(){
        return userService.logout();
    }

    /**
     * 查询当前用户信息
     * @return 用户信息
     */
    @GetMapping
    public Result getById(){
        return  userService.getUser();
    }

    /**
     * 编辑用户数据
     * @param user 更新信息
     * @return null
     */
    @PutMapping
    public Result updateUser(@RequestBody User user){
        return userService.updateUser(user);
    }

    /**
     * 修改密码
     * @param passwordDTO 修改密码表单数据
     * @return null
     */
    @PutMapping("/password")
    public Result updatePassword(@RequestBody PasswordDTO passwordDTO){
        return  userService.updatePassword(passwordDTO);
    }

    @PutMapping("/sign")
    public Result sign(){
        return userService.sign();
    }

    @GetMapping("/sign/details")
    public Result getSignTotal(@RequestParam(value = "year" ,required = false)String  year,
                               @RequestParam(value = "month",required = false)String  month){
        return userService.getSignTotal(year,month);
    }

    /**
     * 测试消息通知功能
     * @return 结果
     */
    @GetMapping("/test-notify")
    public Result testNotify(){
        Long userId = UserHolder.getUser().getId();
        String now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String content = "系统消息：这是一条测试消息，发送时间：" + now;
        log.info("发送测试消息给用户{}: {}", userId, content);
        notificationService.sendToUser(userId, content);
        return Result.success("测试消息已发送");
    }
}
