package com.scutmmq.security;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.scutmmq.dto.ApproveReturnDTO;
import com.scutmmq.dto.RejectReturnDTO;
import com.scutmmq.dto.ReturnApplyDTO;
import com.scutmmq.dto.UserDTO;
import com.scutmmq.entity.*;
import com.scutmmq.enums.AuditStatus;
import com.scutmmq.enums.OrderStatus;
import com.scutmmq.exception.BusinessException;
import com.scutmmq.mapper.*;
import com.scutmmq.service.OrderItemsService;
import com.scutmmq.service.ProductService;
import com.scutmmq.service.ReturnAuditService;
import com.scutmmq.service.UserService;
import com.scutmmq.service.Impl.OderItemsServiceImpl;
import com.scutmmq.service.Impl.OrderServiceImpl;
import com.scutmmq.service.Impl.UserAddressServiceImpl;
import com.scutmmq.service.NotificationService;
import com.scutmmq.utils.RedisUtils;
import com.scutmmq.utils.UserHolder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class IdorSecurityTest {

    // --- OrderServiceImpl mocks ---
    @Mock
    private OrderMapper orderMapper;
    @Mock
    private OrderItemsMapper orderItemsMapper;
    @Mock
    private MerchantUserMapper merchantUserMapper;
    @Mock
    private MerchantMapper merchantMapper;
    @Mock
    private UserService userService;
    @Mock
    private ProductService productService;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ReturnAuditService auditService;
    @Mock
    private OrderItemsService orderItemsService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private RedisUtils redisUtils;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private HashOperations<String, Object, Object> hashOperations;
    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @InjectMocks
    private OrderServiceImpl orderService;

    // --- UserAddressServiceImpl mocks ---
    @Mock
    private UserAddressMapper userAddressMapper;
    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private UserAddressServiceImpl userAddressService;

    // --- OderItemsServiceImpl mocks ---
    @InjectMocks
    private OderItemsServiceImpl oderItemsService;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Orders.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), UserAddress.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ReturnAudit.class);
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "baseMapper", orderMapper);
        ReflectionTestUtils.setField(userAddressService, "baseMapper", userAddressMapper);
        ReflectionTestUtils.setField(oderItemsService, "baseMapper", orderItemsMapper);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    // ==========================================
    // SEC-01 Tests: Order IDOR Protections
    // ==========================================

    @Test
    void testCancelOrderRejectsUnauthorizedUser() {
        UserDTO attacker = new UserDTO();
        attacker.setId(999L);
        UserHolder.saveUser(attacker);

        Orders victimOrder = new Orders();
        victimOrder.setId(1001L);
        victimOrder.setUserId(888L); // 属于受害者
        victimOrder.setStatus(OrderStatus.PENDING);

        when(orderMapper.selectById(1001L)).thenReturn(victimOrder);

        ReturnApplyDTO dto = new ReturnApplyDTO();
        dto.setOrderId(1001L);

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.cancelOrder(dto));
        assertEquals("无权操作该订单", ex.getMessage());
    }

    @Test
    void testCancelOrderAllowedForOrderOwner() {
        UserDTO owner = new UserDTO();
        owner.setId(888L);
        UserHolder.saveUser(owner);

        Orders ownerOrder = new Orders();
        ownerOrder.setId(1001L);
        ownerOrder.setUserId(888L);
        ownerOrder.setStatus(OrderStatus.PENDING);

        when(orderMapper.selectById(1001L)).thenReturn(ownerOrder);
        when(orderMapper.updateById(any(Orders.class))).thenReturn(1);
        when(hashOperations.get(anyString(), anyString())).thenReturn("TEMP_123");
        when(orderItemsMapper.getItemsByOrderId(1001L)).thenReturn(Collections.emptyList());

        ReturnApplyDTO dto = new ReturnApplyDTO();
        dto.setOrderId(1001L);

        Result result = orderService.cancelOrder(dto);
        assertEquals(1, result.getCode());
    }

    @Test
    void testApproveReturnRejectsNonOwnerMerchant() {
        UserDTO user = new UserDTO();
        user.setId(50L);
        UserHolder.saveUser(user);

        // 当前用户持有的商户为 10L
        when(merchantUserMapper.getMerchantIdByUserId(50L)).thenReturn(10L);

        ReturnAudit audit = new ReturnAudit();
        audit.setId(1L);
        audit.setAuditStatus(AuditStatus.PENDING);
        audit.setMerchantId(20L); // 属于商户 20L
        audit.setOrderId(2001L);

        when(auditService.getById(1L)).thenReturn(audit);

        ApproveReturnDTO dto = new ApproveReturnDTO();
        dto.setAuditId(1L);
        dto.setOrderId(2001L);
        dto.setMerchantId(20L); // 攻击者试图伪造传入商户 20L

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.approveReturn(dto));
        assertEquals("无权审批非本店退货申请", ex.getMessage());
    }

    @Test
    void testRejectReturnRejectsNonOwnerMerchant() {
        UserDTO user = new UserDTO();
        user.setId(50L);
        UserHolder.saveUser(user);

        // 当前用户持有的商户为 10L
        when(merchantUserMapper.getMerchantIdByUserId(50L)).thenReturn(10L);

        ReturnAudit audit = new ReturnAudit();
        audit.setId(1L);
        audit.setAuditStatus(AuditStatus.PENDING);
        audit.setMerchantId(20L); // 属于商户 20L
        audit.setOrderId(2001L);

        when(auditService.getById(1L)).thenReturn(audit);

        RejectReturnDTO dto = new RejectReturnDTO();
        dto.setAuditId(1L);
        dto.setOrderId(2001L);
        dto.setMerchantId(20L);

        BusinessException ex = assertThrows(BusinessException.class, () -> orderService.rejectReturn(dto));
        assertEquals("无权审批非本店退货申请", ex.getMessage());
    }

    @Test
    void testGetOrderItemsRejectsUnauthorizedUser() {
        UserDTO attacker = new UserDTO();
        attacker.setId(999L);
        UserHolder.saveUser(attacker);

        Orders victimOrder = new Orders();
        victimOrder.setId(777L);
        victimOrder.setUserId(888L);
        victimOrder.setMerchantId(55L);

        when(orderMapper.selectById(777L)).thenReturn(victimOrder);
        when(merchantUserMapper.getMerchantIdByUserId(999L)).thenReturn(null);

        Result result = oderItemsService.getItemsByOrderId(777L);
        assertEquals(0, result.getCode());
        assertEquals("无权查看该订单详情", result.getMsg());
    }

    // ==========================================
    // SEC-02 Tests: Address Scoping & IDOR
    // ==========================================

    @Test
    void testSetDefaultAddressOnlyResetsForCurrentUser() {
        UserDTO user = new UserDTO();
        user.setId(123L);
        UserHolder.saveUser(user);

        UserAddress targetAddress = new UserAddress();
        targetAddress.setId(999L);
        targetAddress.setUserId(123L);

        when(userAddressMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(targetAddress);
        when(userAddressMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        Result result = userAddressService.defaultAddress(999L);
        assertEquals(1, result.getCode());

        // 验证两次 update 均带上了 userId = 123L 条件
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userAddressMapper, times(2)).update(isNull(), captor.capture());

        for (LambdaUpdateWrapper wrapper : captor.getAllValues()) {
            String sql = wrapper.getCustomSqlSegment();
            assertNotNull(sql);
            assertTrue(sql.contains("user_id"));
        }
    }

    @Test
    void testGetAddressByIdRejectsOtherUserAddress() {
        UserDTO user = new UserDTO();
        user.setId(123L);
        UserHolder.saveUser(user);

        UserAddress otherUserAddress = new UserAddress();
        otherUserAddress.setId(555L);
        otherUserAddress.setUserId(456L); // 属于用户 456

        when(valueOperations.get(anyString())).thenReturn(null);
        when(userAddressMapper.selectById(555L)).thenReturn(otherUserAddress);

        Result result = userAddressService.getAddressById(555L);
        assertEquals(0, result.getCode());
        assertEquals("无权查看该地址", result.getMsg());
    }

    @Test
    void testDeleteAddressScopedToCurrentUser() {
        UserDTO user = new UserDTO();
        user.setId(123L);
        UserHolder.saveUser(user);

        when(userAddressMapper.delete(any(LambdaUpdateWrapper.class))).thenReturn(1);

        Result result = userAddressService.deleteAddress(888L);
        assertEquals(1, result.getCode());

        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(userAddressMapper).delete(captor.capture());
        String sql = captor.getValue().getCustomSqlSegment();
        assertNotNull(sql);
        assertTrue(sql.contains("user_id"));
    }
}
