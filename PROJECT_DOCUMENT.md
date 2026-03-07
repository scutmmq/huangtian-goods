# 在线商城应用项目文档（v2.0）

## 一、需求描述

随着互联网技术的发展和普及，电子商务已成为现代商业的重要组成部分。传统零售业面临着成本高、覆盖范围有限等问题，而线上商城能够突破地域限制，提供更丰富的商品选择和更便捷的购物体验。因此，开发一个功能完善、性能稳定的在线商城系统具有重要的现实意义。

本项目旨在设计并实现一个基于Spring Boot的在线商城系统，满足用户和商家的基本需求。具体功能需求包括：

1. **用户管理**：用户可以注册账号、登录系统、管理个人信息（如收货地址）、修改密码等。
2. **商户管理**：商家可以注册店铺、管理商品信息（增删改查）、查看库存变更记录等。
3. **商品管理**：用户可以浏览商品、搜索商品（按分类、价格区间、关键词等）、查看商品详情；商家可以管理自己的商品。
4. **购物车功能**：用户可以将商品添加到购物车、修改商品数量、删除商品、清空购物车等。
5. **订单管理**：用户可以创建订单、查看订单状态、取消订单、申请退货；商家可以处理订单（发货等）、审核退货申请。
6. **支付功能**：系统需集成主流支付方式（如支付宝、微信支付），支持用户完成订单支付。
7. **评价系统**：用户可以对购买的商品进行评价，商家可以回复评价。
8. **退货审核**：用户可以申请退货，商家可以审核退货申请（批准或拒绝）。
9. **通知系统**：通过WebSocket实现实时消息推送，确保用户和商家能及时收到重要通知。
10. **日志系统**：记录用户操作日志，便于后期分析和审计。
11. **文件管理**：支持图片上传功能，集成阿里云OSS进行文件存储。

系统需要具备良好的用户体验、稳定的数据处理能力和一定的安全性保障，同时要考虑系统的可扩展性，以便未来增加新功能。

## 二、设计思路

### 2.1 系统架构设计
本系统采用前后端分离的架构设计，后端基于Spring Boot框架构建RESTful API，前端使用Vue3框架构建用户界面。整体架构分为表现层、业务逻辑层、数据访问层和数据存储层。

1. **表现层**：前端负责用户界面展示和用户交互，通过HTTP请求与后端进行数据交换。
2. **业务逻辑层**：后端Spring Boot应用负责处理业务逻辑，包括用户管理、商品管理、订单处理等。
3. **数据访问层**：使用MyBatis-Plus作为ORM框架，简化数据库操作。
4. **数据存储层**：使用MySQL作为主数据库存储业务数据，Redis作为缓存数据库提升系统性能。

### 2.2 技术选型
根据系统需求和开发效率考虑，选择了以下技术栈：

- **后端框架**：Spring Boot 3.5.5 - 快速构建企业级应用，简化配置
- **ORM框架**：MyBatis-Plus 3.5.5 - 简化数据库操作，提高开发效率
- **数据库**：MySQL - 成熟的关系型数据库，适合存储结构化数据
- **缓存**：Redis - 提供高性能的缓存服务，支持多种数据结构
- **安全认证**：JWT 0.13.0 - 实现无状态的Token认证机制
- **连接池**：Alibaba Druid 1.2.27 - 高效的数据库连接池
- **工具库**：Hutool 5.8.40 - 简化Java开发的工具类库
- **代码简化**：Lombok - 减少样板代码，提高开发效率
- **分页插件**：PageHelper 1.4.7 - 简化分页查询实现
- **对象存储**：Alibaba Cloud OSS 3.17.4 - 存储图片等静态资源
- **实时通信**：WebSocket - 实现实时消息推送
- **分布式锁**：Redisson 3.22.0 - 实现分布式环境下的数据一致性
- **日志管理**：Logback - 记录系统运行日志

### 2.3 模块划分
根据功能需求，系统主要划分为以下几个模块：

1. **用户管理模块**：负责用户注册、登录、信息管理等功能。
2. **商户管理模块**：负责商户注册、商品管理、库存管理等功能。
3. **商品管理模块**：负责商品展示、搜索、详情查看等功能。
4. **购物车模块**：负责购物车的增删改查操作。
5. **订单管理模块**：负责订单创建、状态管理、退货处理等功能。
6. **支付模块**：负责集成第三方支付接口，处理支付流程。
7. **评价模块**：负责商品评价和商家回复功能。
8. **退货审核模块**：负责处理用户的退货申请和商家的审核操作。
9. **通知模块**：负责向用户和商家推送实时消息。
10. **日志模块**：负责记录用户操作日志。
11. **文件管理模块**：负责处理图片上传等文件操作。

## 三、实现过程

### 3.1 项目初始化
项目开发的第一步是搭建开发环境和初始化项目结构。我们使用IntelliJ IDEA作为开发工具，创建了一个基于Maven的Spring Boot项目。在项目初始化阶段，我们完成了以下工作：
1. 配置pom.xml文件，引入所需的依赖包，包括Spring Boot Web、MyBatis-Plus、MySQL驱动、Redis、JWT、Druid连接池、Hutool工具库、Lombok等。
2. 配置application.yaml文件，设置数据库连接、Redis连接、日志配置等。
3. 设计数据库表结构，创建了用户表、商户表、商品表、订单表等相关数据表，并编写了初始化SQL脚本。

### 3.2 用户管理模块实现
用户管理模块是系统的基础模块，负责用户的注册、登录、信息管理等功能。实现过程如下：
1. **用户注册**：用户填写注册信息，后端验证信息合法性后将用户信息存储到数据库，并生成JWT Token返回给前端。
2. **用户登录**：用户输入用户名和密码，后端验证通过后生成JWT Token并存储到Redis中，同时设置过期时间。
3. **信息管理**：用户可以查看和修改个人信息，包括昵称、头像、收货地址等。
4. **密码修改**：用户可以修改登录密码，需要验证原密码的正确性。
5. **地址管理**：用户可以添加、编辑、删除收货地址，并设置默认地址。

### 3.3 商户管理模块实现
商户管理模块负责商户的注册、认证和商品管理等功能。实现过程如下：
1. **商户注册**：用户可以申请成为商户，填写商户信息并提交审核。
2. **商品管理**：商户可以添加、编辑、删除商品信息，设置商品库存。
3. **库存管理**：商户可以查看商品库存变更记录，了解库存变化情况。

### 3.4 商品管理模块实现
商品管理模块负责商品的展示、搜索和详情查看等功能。实现过程如下：
1. **商品浏览**：用户可以在首页浏览推荐商品，也可以通过分类导航查看不同类别的商品。
2. **商品搜索**：用户可以通过关键词搜索商品，支持按价格区间筛选。
3. **商品详情**：用户可以查看商品的详细信息，包括价格、库存、评价等。
4. **商品收藏**：用户可以收藏感兴趣的商品，方便日后查看。

### 3.5 购物车模块实现
购物车模块允许用户将商品添加到购物车中，方便统一结算。实现过程如下：
1. **添加商品**：用户可以将商品添加到购物车，指定购买数量。
2. **修改数量**：用户可以修改购物车中商品的数量。
3. **删除商品**：用户可以从购物车中删除不需要的商品。
4. **清空购物车**：用户可以一键清空购物车中的所有商品。

### 3.6 订单管理模块实现
订单管理模块负责处理用户的购买流程，包括创建订单、支付、发货、收货等环节。实现过程如下：
1. **创建订单**：用户从购物车中选择商品创建订单，填写收货地址和支付方式。
2. **订单支付**：用户可以选择支付宝或微信支付完成订单支付。
3. **商户发货**：商户在后台查看待发货订单，填写物流信息并发货。
4. **用户收货**：用户收到商品后，在系统中确认收货，订单完成。
5. **退货处理**：用户可以申请退货，商户审核通过后处理退货。

### 3.7 评价模块实现
评价模块允许用户对购买的商品进行评价，帮助其他用户了解商品质量。实现过程如下：
1. **商品评价**：用户在订单完成后可以对商品进行评分和文字评价。
2. **商家回复**：商户可以对用户的评价进行回复，与用户互动。

### 3.8 通知模块实现
通知模块通过WebSocket实现实时消息推送，确保用户和商户能及时收到重要通知。实现过程如下：
1. **消息推送**：系统在订单状态变更、支付成功等关键节点向用户和商户推送通知。
2. **离线消息**：对于离线用户，系统将消息存储在Redis中，用户上线后自动推送。

## 4. 数据库设计

### 4.1 核心数据表
- 用户表(user)：存储用户基本信息
- 用户地址表(user_address)：存储用户收货地址信息
- 商户表(merchant)：存储商户信息
- 商家用户关联表(merchant_user)：存储商户与用户关联关系
- 商品分类表(product_category)：存储商品分类信息
- 商品表(product)：存储商品信息
- 购物车表(carts)：存储用户购物车
- 购物车项表(cart_items)：存储购物车中的商品项
- 订单表(orders)：存储订单信息
- 订单项表(order_items)：存储订单中的商品项
- 商品评价表(product_review)：存储商品评价信息
- 库存变更日志表(inventory_log)：记录商品库存变更历史
- 操作日志表(operation_log)：记录用户操作日志
- 退货审核表(return_audit)：记录退货审核信息

## 5. 安全设计

### 5.1 认证授权
- 基于JWT的Token认证机制
- 用户登录后生成Token并返回给前端
- 请求拦截器验证Token有效性
- 用户信息存储在Redis中，提高访问效率
- Token自动刷新机制（当Token有效期小于20分钟时自动刷新）
- 支持多种登录方式（用户名、邮箱、手机号）

### 5.2 数据安全
- 敏感信息加密存储
- SQL注入防护
- XSS攻击防护
- 参数校验与过滤

## 6. 性能优化

### 6.1 缓存策略
- Redis缓存热点数据（如商品详情、用户信息等）
- 分布式锁保证并发安全（如库存扣减）
- 会话信息存储在Redis中
- 商品库存预占机制（防止超卖）
- 签到数据使用Redis Bitmap存储

### 6.2 数据库优化
- 合理设计索引
- 分页查询优化（使用PageHelper）
- 连接池管理（使用Druid）
- 订单号生成（基于Redis自增序列）

## 四、成果展示

### 4.1 系统功能展示
经过一段时间的开发和测试，本项目已实现了一个功能相对完善的在线商城系统。系统主要包含以下功能模块：

1. **用户管理模块**：实现了用户注册、登录、个人信息管理、收货地址管理等功能。用户可以通过手机号或邮箱注册账号，登录后可以查看和修改个人信息，管理多个收货地址。

2. **商户管理模块**：用户可以申请成为商户，商户可以管理自己的商品信息和库存。系统提供了商户注册、信息审核、商品上下架等功能。

3. **商品管理模块**：实现了商品浏览、搜索、详情查看等功能。用户可以通过分类导航或关键词搜索找到所需商品，查看商品详细信息和评价。

4. **购物车模块**：用户可以将商品添加到购物车，修改商品数量，删除商品或清空购物车。购物车数据与用户账号绑定，方便用户随时查看和管理。

5. **订单管理模块**：用户可以创建订单、查看订单状态、取消订单、申请退货等。商户可以处理订单，包括发货、审核退货申请等操作。

6. **支付模块**：系统集成了主流的支付方式，用户可以方便地完成订单支付。

7. **评价模块**：用户可以对购买的商品进行评价，商户可以回复用户的评价，形成良好的互动交流。

### 4.2 系统界面展示
系统前端采用Vue3框架构建，界面美观、操作流畅。以下是几个主要页面的展示：

1. **首页**：展示了推荐商品、商品分类导航等，用户可以快速浏览和搜索商品。

2. **商品详情页**：详细展示了商品信息、用户评价等，用户可以在此页面将商品加入购物车或直接购买。

3. **购物车页**：展示了用户购物车中的所有商品，用户可以修改数量、删除商品或去结算。

4. **订单页**：展示了用户的订单列表和订单详情，用户可以查看订单状态、取消订单或申请退货。

5. **个人中心页**：用户可以在此页面查看和修改个人信息、管理收货地址、查看收藏商品等。

6. **商户管理页**：商户可以在此页面管理商品信息、查看订单、处理退货申请等。

### 4.3 系统测试结果
通过对系统各项功能进行全面测试，系统运行稳定，各模块功能均能达到预期效果。用户界面友好，操作简便，能够满足用户在线购物的基本需求。系统性能表现良好，在高并发情况下也能保持稳定运行。

## 五、心得体会

通过本次在线商城系统的开发实践，我深刻体会到了软件开发的复杂性和挑战性，同时也收获了许多宝贵的经验。

首先，在技术层面，我更加熟练地掌握了Spring Boot框架的使用，理解了如何构建一个完整的Web应用程序。通过对MyBatis-Plus、Redis、JWT等技术的应用，我对数据库操作、缓存机制、用户认证等有了更深入的理解。特别是在处理高并发场景时，通过使用Redis分布式锁和Lua脚本来保证数据一致性，让我对分布式系统的设计有了初步的认识。

其次，在项目管理和开发流程方面，我学会了如何合理规划项目进度，按照模块化的方式进行开发。从最初的环境搭建、数据库设计，到各个功能模块的实现，再到最后的测试和优化，每一个阶段都需要仔细考虑和精心设计。在开发过程中，我也遇到了许多问题，比如高并发下token刷新混乱、库存超卖等，但通过查阅资料和不断调试，最终都得到了解决，这极大地提升了我的问题解决能力。

此外，本次项目也让我认识到团队协作的重要性。虽然这是一个个人项目，但在开发过程中，我参考了许多开源项目的代码和设计方案，也从社区中获得了不少帮助。这让我意识到，在实际工作中，良好的沟通和协作能力同样重要。

最后，通过这个项目，我不仅巩固了已有的知识，还学习了许多新的技术和理念，为今后的学习和工作打下了坚实的基础。我相信，这些经验和技能将在未来的项目中发挥重要作用。

## 七、附录（关键代码）

### 1. JWT工具类
```java
package com.scutmmq.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.PushBuilder;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

public class JwtUtils {
    private static final String SECRET = "woainizhongguoqinaidemuqinwoweiniliulei";
    private  static  final SecretKey SIGNINGKEY = Keys.hmacShaKeyFor(SECRET.getBytes());
    private  static final  long EXPIRATION_TIME =3600 * 1000;

    public static String generateJwtToken(Map<String,Object>claims){
        return Jwts.builder()
                .signWith(SIGNINGKEY)
                .claims(claims) //自定义数据
                .expiration(new Date(System.currentTimeMillis()+EXPIRATION_TIME))
                .compact();
    }

    public static Claims parseJwtToken(String token){
        return Jwts
                .parser()
                .verifyWith(SIGNINGKEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

### 2. 登录认证拦截器
```java
package com.scutmmq.interceptor;

import com.scutmmq.dto.UserDTO;
import com.scutmmq.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class LoginCertificationInterceptor implements HandlerInterceptor {


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        UserDTO userDTO = UserHolder.getUser();
        if(userDTO==null){
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return  false;
        }
        return true;
    }
}
```

### 3. Web配置类
```java
package com.scutmmq.config;

import com.scutmmq.interceptor.LoginCertificationInterceptor;
import com.scutmmq.interceptor.RefreshInterceptor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Data
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final RefreshInterceptor refreshInterceptor;

    private final LoginCertificationInterceptor loginCertificationInterceptor;


    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(refreshInterceptor)
                .addPathPatterns("/**")
                .order(0);

        registry.addInterceptor(loginCertificationInterceptor)
                .excludePathPatterns("/user/login","/user/register","/image/upload")
                .order(1);
    }
}
```

### 4. Redis工具类
```java
package com.scutmmq.utils;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;

@RequiredArgsConstructor
@Data
@Component
public class RedisUtils {

    private final StringRedisTemplate redisTemplate;

    private static final DefaultRedisScript<Long> RESERVE_STOCK_SCRIPT;

    private static final DefaultRedisScript<Long> CANCEL_RESERVE_STOCK_SCRIPT;

    private static final  DefaultRedisScript<Long> ROLLBACK_RESERVE_STOCK_SCRIPT;
    //synchronizeUpdateStock
    private static final DefaultRedisScript<Long> SYNCHRONIZE_UPDATE_STOCK_SCTIPT;

    static {
        RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>();
        RESERVE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/reserve-stock.lua"));
        RESERVE_STOCK_SCRIPT.setResultType(Long.class);

        CANCEL_RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>();
        CANCEL_RESERVE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/cancel-reserve-stock.lua"));
        CANCEL_RESERVE_STOCK_SCRIPT.setResultType(Long.class);

        ROLLBACK_RESERVE_STOCK_SCRIPT = new DefaultRedisScript<>();
        ROLLBACK_RESERVE_STOCK_SCRIPT.setLocation(new ClassPathResource("lua/rollback-reserve-stock.lua"));
        ROLLBACK_RESERVE_STOCK_SCRIPT.setResultType(Long.class);

        SYNCHRONIZE_UPDATE_STOCK_SCTIPT=new DefaultRedisScript<>();
        SYNCHRONIZE_UPDATE_STOCK_SCTIPT.setResultType(Long.class);
        SYNCHRONIZE_UPDATE_STOCK_SCTIPT.setLocation(new ClassPathResource("lua/update-stock.lua"));
    }

    /**
     * 下单前校验库存(预占)
     * @param productId 商品id
     * @param quantity 购买数量
     * @return 1 校验通过 0 校验不通过,已被他人下单
     */
    public  Long ReserveStock(Long productId,Integer quantity,String tempOrderId){
        return redisTemplate.execute(
                RESERVE_STOCK_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),String.valueOf(quantity),tempOrderId);
    }

    public Long CancelReserveStock(Long productId, String tempOrderId,Long orderId) {
        return redisTemplate.execute(
                CANCEL_RESERVE_STOCK_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),tempOrderId,String.valueOf(orderId)
        );
    }

    public Long rollBackReserveStock(Long productId, String tempOrderId, Long orderId) {
        return redisTemplate.execute(
                ROLLBACK_RESERVE_STOCK_SCRIPT,
                Collections.emptyList(),
                String.valueOf(productId),tempOrderId,String.valueOf(orderId)
        );
    }

    public void synchronizeUpdateStock(Long productId, Integer currentQuantity) {
        redisTemplate.execute(
                SYNCHRONIZE_UPDATE_STOCK_SCTIPT,
                Collections.emptyList(),
                String.valueOf(productId),currentQuantity.toString()
        );
    }
}
```

### 5. Lua脚本（库存预占）
```lua
local productId = ARGV[1]

local quantity = tonumber(ARGV[2])

local tempOrderId = ARGV[3]

local stockKey = "product:stock:available:" ..productId

local stockReserveKey = "product:stock:reserve:" ..productId

-- 查询当前库存
local stockQuantity = tonumber(redis.call('get',stockKey))

-- 判断当前库存是否小于0
if(stockQuantity<=0) then
    return 0
end

-- 判断当前库存是否足够
if(stockQuantity<quantity) then
    return 0 -- 不足够
end

-- 预占库存足够,扣减库存，预占库存

-- 扣减库存
redis.call("decrby",stockKey,quantity)

-- 预占库存
redis.call('hset',stockReserveKey,tempOrderId,quantity)

return 1
```