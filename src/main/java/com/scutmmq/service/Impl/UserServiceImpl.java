package com.scutmmq.service.Impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.scutmmq.anno.LogAnnotation;
import com.scutmmq.dto.LoginDTO;
import com.scutmmq.dto.PasswordDTO;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.User;
import com.scutmmq.enums.LoginType;
import com.scutmmq.mapper.UserMapper;
import com.scutmmq.service.UserService;
import com.scutmmq.utils.JwtUtils;
import com.scutmmq.utils.RedisConstants;
import com.scutmmq.utils.SystemConstants;
import com.scutmmq.utils.UserHolder;
import com.scutmmq.vo.SignResult;
import com.scutmmq.vo.UserVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static com.scutmmq.utils.RedisConstants.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper,User> implements UserService {

    private  final  StringRedisTemplate stringRedisTemplate;

    @Override
    public Result login(LoginDTO loginDTO) {
        // 查询数据库
        LoginType loginType = loginDTO.getLoginType();
        System.out.println(loginType);
        LambdaQueryChainWrapper<User> wrapper = lambdaQuery();
        wrapper.select(User::getId,User::getNickName,User::getImage,User::getUsername);
        // 0用户名登录 1邮箱登录 2电话号码登录
        if(loginType==LoginType.USERNAME){
            wrapper.eq(User::getUsername,loginDTO.getLogin());
        }else if(loginType==LoginType.EMAIL){
            wrapper.eq(User::getEmail,loginDTO.getLogin());
        }else if(loginType==LoginType.PHONE){
            wrapper.eq(User::getPhone,loginDTO.getLogin());
        }

        wrapper.eq(User::getPassword,loginDTO.getPassword());
        UserDTO userDTO = BeanUtil.copyProperties(wrapper.one(),UserDTO.class);
        if(userDTO==null){
            return  Result.error("账号不存在或者密码错误");
        }


        log.info("{}登录成功!",userDTO.getNickName());

        // 设置token
        Map<String,Object>claims =  new HashMap<>();
        claims.put("id",userDTO.getId());
        claims.put("nickName",userDTO.getNickName());
        claims.put("image",userDTO.getImage());
        claims.put("username",userDTO.getUsername());
        String token =JwtUtils.generateJwtToken(claims);
        userDTO.setToken(token);

        // 将token存入redis
        stringRedisTemplate.opsForValue().set(TOKEN_KEY+token,USER_PERMISSION,TOKEN_EXPIRATION,TOKEN_TIME_UNIT);

        // 前端得到对象保存到localstorage
        return Result.success(userDTO);
    }

    @Override
    public Result logout() {

        // 获取用户携带的token
        final UserDTO user = UserHolder.getUser();
        String token = user.getToken();
        log.info("退出登录,此时的token:{}",token);

        // 删除redis的token
        final Boolean deleted = stringRedisTemplate.delete(TOKEN_KEY + token);

        // 删除用户缓存
        stringRedisTemplate.delete(CACHE_USER+user.getId());

        // 更新最后一次登录时间
        final boolean updated = lambdaUpdate().set(User::getLastLogin, LocalDateTime.now()).eq(User::getId, user.getId()).update();


        if(!deleted||!updated){
            return Result.error("退出登录失败!");
        }


        log.info("用户{}退出登录",user.getNickName());
        return Result.success();
    }

    @Override
    public Result register(User user) {
        String nickName = user.getNickName();
        if(nickName==null||nickName.isEmpty()){
            nickName = SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10).toLowerCase();
        }
        user.setNickName(nickName);
        save(user);
        return Result.success();
    }

    @Override
    public Result getUser(){
        Long id = UserHolder.getUser().getId();

        String jsonUser = stringRedisTemplate.opsForValue().get(CACHE_USER + id);

        if(jsonUser==null||jsonUser.isEmpty()){
            log.info("用户缓存未命中 ");
            final User user = getById(id);

            UserVO userVO = BeanUtil.copyProperties(user,UserVO.class);

            // 存入redis缓存
            stringRedisTemplate.opsForValue().set(CACHE_USER+id,JSONUtil.toJsonStr(userVO),30, TimeUnit.MINUTES);

            return Result.success(userVO);
        }
        log.info("用户缓存命中");
         return  Result.success(JSONUtil.toBean(jsonUser,UserVO.class));
    }

    @Override
    public Result updateUser(User user) {
        Long id = UserHolder.getUser().getId();
        final boolean updated = lambdaUpdate()
                .set(user.getEmail() != null && !user.getEmail().isEmpty(), User::getEmail, user.getEmail())
                .set(user.getPhone() != null && !user.getPhone().isEmpty(), User::getPhone, user.getPhone())
                .set(user.getBirthday() != null, User::getBirthday, user.getBirthday())
                .set(user.getNickName() != null && !user.getNickName().isEmpty(), User::getNickName, user.getNickName())
                .set(user.getAddress() != null && !user.getAddress().isEmpty(), User::getAddress, user.getAddress())
                .set(user.getImage()!=null&&!user.getImage().isEmpty(),User::getImage,user.getImage())
                .set(user.getGender()!=null,User::getGender,user.getGender())
                .set(User::getUpdatedTime, LocalDateTime.now())
                .eq(User::getId,id)
                .update();
        if(!updated){
            return  Result.error("更改失败");
        }
        // 更改成功

        // 删除原来的缓存
        stringRedisTemplate.delete(CACHE_USER+id);

        return  Result.success();
    }

    @Override
    public Result updatePassword(PasswordDTO passwordDTO) {
        Long id = UserHolder.getUser().getId();
        final User user = lambdaQuery().select(User::getPassword).eq(User::getId, id).one();

        if(!Objects.equals(passwordDTO.getOldPassword(), user.getPassword())){
            return Result.error("旧密码错误！");
        }
        if(Objects.equals(passwordDTO.getNewPassword(),user.getPassword())){
            return Result.error("新密码不许与旧密码相同!");
        }

        final boolean updated = lambdaUpdate().set(User::getPassword, passwordDTO.getNewPassword()).eq(User::getId, id).update();

        if(!updated){
            return Result.error("修改失败，请联系客服");
        }

        // 强行退出登录
        stringRedisTemplate.delete(TOKEN_KEY+UserHolder.getUser().getToken());

        return Result.success();

    }

    @Override
    public Result sign() {

        Long userId = UserHolder.getUser().getId();

        LocalDateTime now = LocalDateTime.now();
        String format = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key =RedisConstants.SIGN_KEY + String.valueOf(userId) + format;

        // 今天是第几天
        int dayOfMouth = now.getDayOfMonth();

        // 签到
        final Boolean aBoolean = stringRedisTemplate.opsForValue().setBit(key
                , dayOfMouth-1,true ); // 第0位是第一天
        if(aBoolean==null){
            return Result.error("签到失败");
        }
        return Result.success();
    }

    @Override
    public Result getSignTotal(String year, String month) {
        // 获取用户id
        Long userId = UserHolder.getUser().getId();
        // 获取日期
        LocalDateTime now = LocalDateTime.now();
        List<Integer> signedDays = new ArrayList<>();

        if (year!=null&&month!=null){
            // 其它年月的签到表
            String key = RedisConstants.SIGN_KEY + String.valueOf(userId) + ":" + year + "-" + month;
            // 统计签到的日期
            for (int offset = 0; offset < 31; offset++){
                final Boolean aBoolean = stringRedisTemplate.opsForValue().getBit(key, offset);
                if(Boolean.TRUE.equals(aBoolean)){
                    signedDays.add(offset+1);
                }
            }
            return Result.success(signedDays);
        }

        String format = now.format(DateTimeFormatter.ofPattern(":yyyy-MM"));
        String key =RedisConstants.SIGN_KEY + String.valueOf(userId) + format;
        int dayOfMouth = now.getDayOfMonth();

        final List<Long> result = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create()
                .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMouth-1)).valueAt(0));


        if(result==null||result.isEmpty()){
           return Result.success(SignResult.none());
        }
        Long num = null;
        num = result.get(0);

        int countTotal = 0;
        int countContinue = 0;

        // 连续签到：若今天已签，则从今天往前统计；否则从昨天往前统计
        Boolean todaySigned = stringRedisTemplate.opsForValue().getBit(key, dayOfMouth - 1);
        int startOffset = Boolean.TRUE.equals(todaySigned) ? dayOfMouth - 1 : dayOfMouth - 2;
        for (int i = startOffset; i >= 0; i--) {
            Boolean signed = stringRedisTemplate.opsForValue().getBit(key, i);
            if (Boolean.TRUE.equals(signed)) {
                countContinue++;
            } else {
                break;
            }
        }

        // 统计签到的日期
        for (int offset = 0; offset < dayOfMouth; offset++){
            final Boolean aBoolean = stringRedisTemplate.opsForValue().getBit(key, offset);
            if(Boolean.TRUE.equals(aBoolean)){
                signedDays.add(offset+1);
                countTotal++;
            }
        }

        return Result.success(SignResult.of(countTotal,countContinue,signedDays));
    }




}
