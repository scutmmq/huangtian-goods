package com.scutmmq.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.scutmmq.dto.LoginDTO;
import com.scutmmq.dto.PasswordDTO;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.Result;
import com.scutmmq.entity.User;
import com.scutmmq.enums.LoginType;
import com.scutmmq.mapper.UserMapper;
import com.scutmmq.service.Impl.UserServiceImpl;
import com.scutmmq.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class PasswordSecurityTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @InjectMocks
    private UserServiceImpl userService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), User.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userService, "baseMapper", userMapper);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    @Test
    void testRegisterHashesPasswordWithBcrypt() {
        User user = new User();
        user.setUsername("testuser");
        user.setPassword("rawPassword123");

        when(userMapper.insert(any(User.class))).thenReturn(1);

        Result result = userService.register(user);
        assertEquals(1, result.getCode());
        assertNotEquals("rawPassword123", user.getPassword());
        assertTrue(passwordEncoder.matches("rawPassword123", user.getPassword()));
        assertTrue(user.getPassword().startsWith("$2a$") || user.getPassword().startsWith("$2b$"));
    }

    @Test
    void testLoginWithBcryptPasswordSuccess() {
        String raw = "mySecretPassword";
        String encoded = passwordEncoder.encode(raw);

        User mockUser = new User();
        mockUser.setId(100L);
        mockUser.setUsername("testuser");
        mockUser.setNickName("测试昵称");
        mockUser.setPassword(encoded);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setLoginType(LoginType.USERNAME);
        loginDTO.setLogin("testuser");
        loginDTO.setPassword(raw);

        Result result = userService.login(loginDTO);
        assertEquals(1, result.getCode());
        assertNotNull(result.getData());
        UserDTO userDTO = (UserDTO) result.getData();
        assertEquals(100L, userDTO.getId());
        assertNotNull(userDTO.getToken());
    }

    @Test
    void testLoginWithWrongPasswordFails() {
        String raw = "mySecretPassword";
        String encoded = passwordEncoder.encode(raw);

        User mockUser = new User();
        mockUser.setId(100L);
        mockUser.setUsername("testuser");
        mockUser.setPassword(encoded);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setLoginType(LoginType.USERNAME);
        loginDTO.setLogin("testuser");
        loginDTO.setPassword("wrongPassword");

        Result result = userService.login(loginDTO);
        assertEquals(0, result.getCode());
        assertEquals("账号不存在或者密码错误", result.getMsg());
    }

    @Test
    void testLoginWithLegacyPlainTextPasswordAutoUpgrades() {
        String raw = "legacyPlainTextPass";

        User mockUser = new User();
        mockUser.setId(200L);
        mockUser.setUsername("legacyuser");
        mockUser.setNickName("老用户");
        mockUser.setPassword(raw); // 存量明文

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(mockUser);
        when(userMapper.update(any(), any())).thenReturn(1);

        LoginDTO loginDTO = new LoginDTO();
        loginDTO.setLoginType(LoginType.USERNAME);
        loginDTO.setLogin("legacyuser");
        loginDTO.setPassword(raw);

        Result result = userService.login(loginDTO);
        assertEquals(1, result.getCode());
        // 验证旧密码已被平滑重写为 BCrypt
        assertTrue(passwordEncoder.matches(raw, mockUser.getPassword()));
        assertTrue(mockUser.getPassword().startsWith("$2a$") || mockUser.getPassword().startsWith("$2b$"));
    }

    @Test
    void testUpdatePasswordSuccess() {
        UserDTO current = new UserDTO();
        current.setId(300L);
        current.setToken("tok123");
        UserHolder.saveUser(current);

        String oldRaw = "oldPass123";
        String newRaw = "newPass456";
        String oldHashed = passwordEncoder.encode(oldRaw);

        User userInDb = new User();
        userInDb.setId(300L);
        userInDb.setPassword(oldHashed);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(userInDb);
        when(userMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword(oldRaw);
        dto.setNewPassword(newRaw);

        Result result = userService.updatePassword(dto);
        assertEquals(1, result.getCode());
        verify(stringRedisTemplate, times(2)).delete(anyString());
    }

    @Test
    void testUpdatePasswordWithWrongOldPasswordFails() {
        UserDTO current = new UserDTO();
        current.setId(300L);
        UserHolder.saveUser(current);

        User userInDb = new User();
        userInDb.setId(300L);
        userInDb.setPassword(passwordEncoder.encode("correctPass"));

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(userInDb);

        PasswordDTO dto = new PasswordDTO();
        dto.setOldPassword("incorrectPass");
        dto.setNewPassword("newPass");

        Result result = userService.updatePassword(dto);
        assertEquals(0, result.getCode());
        assertEquals("旧密码错误！", result.getMsg());
    }
}
