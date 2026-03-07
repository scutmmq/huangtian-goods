# 线上购物系统 - 开发日志

## 项目信息
- **项目名称**: 线上购物系统
- **技术栈**: Vue3 + Element-plus + Java + MyBatis Plus + MySQL + Nginx + Redis
- **开始日期**: 2025.11.9
- **开发人员**: [莫明钦]

## 开发进度总览

---
### Redis的使用
- [x] 存储token实现有状态的jwt令牌 string
- [x] 缓存商品详情 string
- [x] 缓存用户信息 string
- [x] 生成订单号并记录当天订单的数量 string
- [x] 缓存库存日志 string
- [x] 存储收藏商品 sortedSet
---

### 已完成功能
- [x] 后端项目环境搭建 `2025.11.9`
- [x] 数据库设计 `2025.11.9`
- [x] 用户认证模块 `2025.11.10`
- [x] 用户管理模块 `202511.11`
- [x] 商品管理模块 `2025.11.11`
- [x] 购物车功能 `2025.11.11`
- [x] 订单系统
- [ ] 小程序模块
- [x] 前端页面环境搭建 `2025.11.12-2025.11.26`
- [x] 前后端联调 + 测试
- [ ] 优化后端性能(缓存，高并发锁，消息队列，收藏功能，消息队列)
- [ ] 部署上线
- (若有余力,拓展功能)
- [ ] 解决缓存 穿透、击穿、雪崩
- [ ] 评论系统
- [ ] 点赞关注粉丝功能
- [ ] 抢购优惠券 Stream流
- [ ] 定位系统 GeoLocation
- [ ] 签到系统 Bitmap


第1周：环境搭建 + 基础框架    
第2周：后端核心功能开发      
第3周：前端页面开发    
第4周：前后端联调 + 测试    
第5周：部署上线 + 优化

### 当前进度
**总体进度**: `25%`
**预计完成时间**: 2025年12月5日

---

## 详细开发记录

### 2025年11月9日 (第1天)

#### 今日目标
- [x] 后端项目环境搭建
- [x] 数据库设计

#### 完成工作

1. 项目初始化

- Srping项目的application.yaml配置文件
```yaml
spring:
  application:
    name: online-mall-application
  datasource:
    url: jdbc:mysql://localhost:3306/online_mall
    driver-class-name: com.mysql.jdbc.Driver
    username: ${MYSQL_USERNAME}
    password: ${MYSQL_PASSWORD}
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 10
          max-idle: 10
          min-idle: 1
          time-between-eviction-runs: 10s
      database: 1
mybatis-plus:
  mapper-locations: classpath*:/com/scutmmq/mapper/**/*.xml
  type-aliases-package: com.scutmmq.entity
```

- logback配置文件
```xml
<configuration>
    <!--  控制台输出  -->
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <!-- 格式化输出：%d 表示日期，%thread 表示线程名，%-5level表示级别从左显示5个字符宽度，%logger显示日志记录器的名称， %msg表示日志消息，%n表示换行符  -->
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50}-%msg%n</pattern>
        </encoder>
    </appender>
    <!--  系统文件输出  -->
    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
            <!--  日志文件输出的文件名, %i表示序号  -->
            <FileNamePattern>E:\Study\IT\IDEA_Project\online-mall-application\log\online-shopping-%d{yyyy-MM-dd}-%i.log</FileNamePattern>
            <!--  最多保留的历史日志文件数量  -->
            <MaxHistory>30</MaxHistory>
            <!--  最大文件大小，超过这个大小会触发滚动到新文件，默认为 10MB  -->
            <maxFileSize>10MB</maxFileSize>
        </rollingPolicy>
        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
            <!-- 格式化输出：%d 表示日期，%thread 表示线程名，%-5level表示级别从左显示5个字符宽度，%msg表示日志消息，%n表示换行符  -->
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50}-%msg%n</pattern>
        </encoder>
    </appender>
    <!--  日志输出级别  -->
    <root level="info">
        <appender-ref ref="STDOUT"/>
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

- pom.xml 依赖管理
2. 创建基本开发目录
![](./online-mall-images/construction.png)

3. 创建数据库表
[查看表文件](./online_mall.sql)    
用户层 (User Layer)    
├── user (用户表)    
└── user_address (用户地址表)    

商家层 (Merchant Layer)      
├── merchant (商家表)    
└── merchant_user (商家用户关联表)    

商品层 (Product Layer)    
├── product_category (商品分类表)    
├── product (商品表)    
└── inventory_logs (库存变更记录表)    

交易层 (Transaction Layer)    
├── carts (购物车表)    
├── cart_items (购物车项表)    
├── orders (订单表)    
├── order_items (订单项表)    
└── payment_records (支付记录表)    


- 表依赖关系总览

| 子表 | 父表 | 外键字段 | 删除策略 | 业务说明 |
|------|------|----------|----------|----------|
| **user_address** | user | user_id | CASCADE | 用户删除时同步删除地址 |
| **merchant_user** | user | user_id | RESTRICT | 用户与商家的关联关系 |
| **merchant_user** | merchant | merchant_id | RESTRICT | 商家与用户的权限关联 |
| **carts** | user | user_id | CASCADE | 用户删除时同步删除购物车 |
| **product** | merchant | merchant_id | RESTRICT | 商品必须属于某个商家 |
| **product** | product_category | category_id | RESTRICT | 商品必须属于某个分类 |
| **inventory_logs** | product | product_id | RESTRICT | 库存变更记录关联商品 |
| **cart_items** | carts | cart_id | CASCADE | 购物车删除时同步删除项 |
| **cart_items** | product | product_id | CASCADE | 商品删除时同步移除购物车项 |
| **orders** | user | user_id | RESTRICT | 保护用户的历史订单 |
| **orders** | merchant | merchant_id | RESTRICT | 订单必须属于某个商家 |
| **orders** | user_address | shipping_address_id | RESTRICT | 订单收货地址快照 |
| **orders** | user_address | billing_address_id | RESTRICT | 订单账单地址快照 |
| **order_items** | orders | order_id | CASCADE | 订单删除时同步删除订单项 |
| **order_items** | product | product_id | RESTRICT | 保护订单项的商品关联 |
| **order_items** | merchant | merchant_id | RESTRICT | 订单项商家快照 |
| **payment_records** | orders | order_id | RESTRICT | 支付记录关联订单 |


- 级联删除路径
```
user (删除)
  ↓ CASCADE
user_address (同步删除)
carts (同步删除)
  ↓ CASCADE  
cart_items (同步删除)

orders (删除)
  ↓ CASCADE
order_items (同步删除)
```

- 受保护的关系（RESTRICT）
```
user ─┐
      ├→ orders (受保护)
      └→ merchant_user (受保护)

merchant ─┐
          ├→ product (受保护)
          ├→ orders (受保护)
          └→ merchant_user (受保护)

product ─┐
         ├→ order_items (受保护)
         └→ inventory_logs (受保护)

product_category → product (受保护)
```
4. 连接MySQL数据库
5. 连接Redis


### 2025年11月10日 (第2天)

#### 今日目标
- [x] 用户认证模块

#### 完成工作

- `使用JWT+Redis实现有状态的登录认证`

1. JWT令牌生成与解析 
2. 登录成功生成JWT令牌
3. ThreadLocal存储用户信息
4. 认证拦截器

- [x] `15:32已完成`



### 2025年11月11日 (第3天)

#### 今日目标
- [x] 用户管理模块
- [x] 商品管理模块
- [x] 购物车功能

#### 完成工作

1. 用户管理接口
2. 商品管理接口
3. 购物车管理接口
4. 商家商户管理接口


- 订单系统设计逻辑复杂，选择先做前端基础页面，到时候结合前端页面来设计订单系统逻辑

### 2025年11月12日

#### 今日目标
- [x] 用户登录以及认证模块


#### 完成工作
1. 前端登录功能页面设计
2. 登录功能实现
3. 前端拦截器认证功能实现
4. 主页设计

### 2025年11月13日

#### 今日目标
- [x] 前端用户注册功能
- [x] 前端用户地址管理功能

#### 完成工作

1. 用户注册功能
2. 用户地址管理
3. 后端用户信息缓存
4. 解决token白名单由于高并发访问无法删除的情况(给旧token设置30s过期时间而不是直接删除)


### 2025年11月16日

#### 今日目标
- [x] 前端展示商品页面(分类和商品)基本功能
- [x] 商品关键词搜索功能
- [x] 商品详情页面展示  

#### 完成工作

1. 完成目标所有的基本框架，后续功能实现完善了逐步优化页面展示


### 2025年11月17日

#### 今日目标
- [x] 前端购物车功能实现(添加取消查看)
- [x] 后端服务器订单功能框架实现
- (若有余力)
- [ ] 商家注册前端实现
- [ ] AOP基本实现
- [x] 用户点赞商品功能(redis实现)


### 2025年11月18日

#### 今日目标
- [x] 展示我的收藏商品
- [x] 前后端商家注册功能实现
- [x] 后端添加商品功能实现
- (若有余力)
- [ ] AOP基本实现
- [x] 前端商品功能实现
#### 完成任务
1. 使用redis的sortedSet实现了商品收藏与展示收藏商品
2. 商家注册功能实现
3. 商家信息编辑功能实现
4. 商品编辑功能实现
5. 商品添加功能实现
6. 解决了商家和商品评分展示的混乱
- `遇到的疑惑：高并发情况下token刷新混乱，导致前端刷新到的token是即将要从redis过期的黑名单token`


### 2025年11月19日

#### 今日目标
- [x] 解决高并发下redis+jwt的有状态令牌的bug
- [ ] AOP学习+实现
- (若有余力)
- [ ] 库存管理
- [ ] 点击进入商家主页

#### 完成任务
1. 添加过期时间校验，当有效期<10min时才刷新token，避免高并发反复刷新token造成的
性能耗损和token获取混乱
2. 库存管理日志前后端实现


### 2025年11月20日

#### 今日目标
-[ ] 实现用户下单、付款、确认收获、退货的功能 `50%`
-[ ] 实现商家确认发货、确认退货功能 `50%`
- 流程如下:
用户下单,传入商品信息(待支付)-->用户支付(已支付)-->商家确认、指定发货地址、指定发货方式、
发货时间(已发货)-->用户确认收获，订单完成(已送达)

#### 完成任务
1. 用户下单
2. 展示订单详情页面
3. 用户支付（模拟）
4. 用户取消支付
5. 退货功能（目前不完善，需要处理商家确认退货才行，并且库存要加回去）
6. 商家确认发货
7. 展示用户的历史订单

### 2025年11月21日

#### 今日目标（未完成）
- [ ] 用户退货&&商家确认退货
- [ ] 主页展示历史消息提醒商家处理未发货和待审核的订单


### 2025年11月22日-2025年11月23日（周末）

#### 今日目标
- [x] 商家获取订单备注（增加数据库字段）
- [x] 添加审核表，展示审核信息审核结果
- [ ] 消息队列处理订单并且使用websocket传送给商家提醒
- [ ] 审核处理，例如用户退货理由，商家获取退货理由，商家拒绝退货后的订单状态，商家同意后的订单状态
商家不同意的理由
- [ ] 交易完成后商家的销售额、销售量的改变


- 后续目标：用户完成购物后的评分、评论、websocket顾客与商家交流（太难就不做了）,之前代码不足的补充，例如评分的计算

### 2025年11月24-11月25

#### 今日目标
- [x] 前端页面联调测试，主要是订单系统

#### 完成任务
1. 完成前端页面与后端的联调测试


### 2025年11月26日

#### 今日目标
- [x] redis存储库存
- [x] 生成订单后，支付订单前，预存订单（redis级别），支付订单后，再扣减库存
- [x] 悲观锁避免超卖
- [x] 使用锁+redis+lua脚本校验下单资格并预占，校验通过下单
- [ ] 使用stream流实现消息队列来完成订单超时取消的功能

#### 完成任务

1. 使用锁（productId作为粒度）+redis+lua实现了下单校验库存并且预占库存
2. 支付成功后，处理预占库存
3. 取消支付后，处理预占库存
4. 申请退货后，同步redis库存
5. 下单后，添加预占库存
6. 不支持同个商家多种商品购买，会出现redis修改了但是发现后来的商品不足，无法回退的现象


### 2025年11月27日

#### 今日目标
- [x] 实现redis存储库存的同步更新策略
- [x] 使用stream流实现消息队列来完成订单超时取消的功能
- [ ] 初步使用websocket

#### 完成任务
1. 基于stream消息队列和sortedSet延时检测实现了自动处理超时订单功能
2. 实现了当更改库存后读取预占库存实时更新redis存储的活跃库存

### 2025年11月28-2025年12月3日

#### 目标
- [x] 实现订单状态改变后在线发送消息和实时发送消息(websocket)
- [x] 实现日历签到功能
#### 完成任务
1. 订单状态改变后，实时发送消息给用户和商家
2. 使用redis的bitmap实现了日历签到功能


### 2025年12月5日

#### 今日任务
- [ ] 评论系统
- [ ] 评分系统
- [ ] 销量管理
- [ ] 界面美化