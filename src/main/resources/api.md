# 在线商城系统 RESTful API 接口文档

## 文档概述

本文档提供了在线商城系统的完整 RESTful API 接口说明，包含用户管理、商品管理、订单管理、购物车管理等核心功能。

**基础信息**
- 基础URL: `https://api.onlinemall.com/v1`
- 编码格式: UTF-8
- 数据格式: JSON
- 认证方式: JWT Token

## 1. 认证授权

### 1.1 用户注册
**POST** `/auth/register`

**请求参数:**
```json
{
  "username": "string, required, 用户名",
  "password": "string, required, 密码",
  "email": "string, optional, 邮箱",
  "phone": "string, required, 手机号",
  "nickName": "string, optional, 昵称",
  "gender": "number, optional, 性别 0-女 1-男 2-其他"
}
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "phone": "13800138000",
    "nickName": "随机昵称",
    "createdTime": "2024-01-01 10:00:00"
  }
}
```

### 1.2 用户登录
**POST** `/auth/login`

**请求参数:**
```json
{
  "login": "string, required, 用户名/邮箱/手机号",
  "password": "string, required, 密码",
  "loginType": "int optional  1用户名 2邮箱 3手机号"
}
```

**响应:**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "user": {
      "id": 1,
      "username": "testuser",
      "email": "test@example.com",
      "phone": "13800138000",
      "nickName": "用户昵称"
    }
  }
}
```

### 1.3 刷新Token
**POST** `/auth/refresh`

**Headers:**
```
Authorization: Bearer {refresh_token}
```

## 2. 用户管理

### 2.1 获取用户信息
**GET** `/users/profile`

**Headers:**
```
Authorization: Bearer {token}
```

**响应:**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "username": "testuser",
    "email": "test@example.com",
    "gender": 1,
    "birthday": "1990-01-01",
    "nickName": "用户昵称",
    "phone": "13800138000",
    "image": "https://example.com/avatar.jpg",
    "address": "北京市朝阳区",
    "lastLogin": "2024-01-01 10:00:00"
  }
}
```

### 2.2 更新用户信息
**PUT** `/users/profile`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "email": "string, optional",
  "gender": "number, optional",
  "birthday": "string, optional, YYYY-MM-DD",
  "nickName": "string, optional",
  "image": "string, optional",
  "address": "string, optional"
}
```

### 2.3 修改密码
**PUT** `/users/password`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "oldPassword": "string, required, 原密码",
  "newPassword": "string, required, 新密码"
}
```

## 3. 用户地址管理

### 3.1 获取地址列表
**GET** `/users/addresses`

**Headers:**
```
Authorization: Bearer {token}
```

**响应:**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "recipient": "张三",
        "phone": "13800138000",
        "province": "北京市",
        "city": "北京市",
        "district": "朝阳区",
        "detail": "建国路100号",
        "postalCode": "100000",
        "isDefault": 1
      }
    ],
    "total": 1
  }
}
```

### 3.2 添加地址
**POST** `/users/addresses`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "recipient": "string, required, 收件人",
  "phone": "string, required, 手机号",
  "province": "string, required, 省份",
  "city": "string, required, 城市",
  "district": "string, optional, 区县",
  "detail": "string, required, 详细地址",
  "postalCode": "string, optional, 邮编",
  "isDefault": "boolean, optional, 是否默认"
}
```

### 3.3 更新地址
**PUT** `/users/addresses/{addressId}`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:** 同添加地址

### 3.4 删除地址
**DELETE** `/users/addresses/{addressId}`

**Headers:**
```
Authorization: Bearer {token}
```

### 3.5 设置默认地址
**PUT** `/users/addresses/{addressId}/default`

**Headers:**
```
Authorization: Bearer {token}
```

## 4. 商品分类

### 4.1 获取分类列表
**GET** `/categories`

**查询参数:**
- `parentId`: number, optional, 父分类ID
- `level`: number, optional, 分类级别
- `status`: number, optional, 状态

**响应:**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "name": "电子产品",
        "parentId": 0,
        "level": 1,
        "sort": 1,
        "icon": "/icons/electronic.png",
        "description": "手机、电脑等数码产品",
        "children": [
          {
            "id": 2,
            "name": "手机",
            "parentId": 1,
            "level": 2,
            "sort": 1
          }
        ]
      }
    ]
  }
}
```

### 4.2 获取分类详情
**GET** `/categories/{categoryId}`

## 5. 商品管理

### 5.1 获取商品列表
**GET** `/products`

**查询参数:**
- `categoryId`: number, optional, 分类ID
- `merchantId`: number, optional, 商家ID
- `keyword`: string, optional, 搜索关键词
- `minPrice`: number, optional, 最低价格
- `maxPrice`: number, optional, 最高价格
- `isActive`: number, optional, 是否上架
- `page`: number, optional, 页码
- `size`: number, optional, 每页大小
- `sort`: string, optional, 排序字段

**响应:**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "name": "小米14 Pro",
        "description": "最新款小米手机",
        "price": 4999.00,
        "stockQuantity": 100,
        "categoryId": 3,
        "sku": "XM14PRO256",
        "imageUrl": "https://example.com/xiaomi14pro.jpg",
        "isActive": 1,
        "merchantId": 1,
        "merchantName": "小米官方旗舰店",
        "categoryName": "小米手机",
        "rating": 4.8
      }
    ],
    "pagination": {
      "page": 1,
      "size": 20,
      "total": 100,
      "pages": 5
    }
  }
}
```

### 5.2 获取商品详情
**GET** `/products/{productId}`

**响应:**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "name": "小米14 Pro",
    "description": "详细商品描述...",
    "price": 4999.00,
    "stockQuantity": 100,
    "categoryId": 3,
    "sku": "XM14PRO256",
    "imageUrl": "https://example.com/xiaomi14pro.jpg",
    "isActive": 1,
    "merchantId": 1,
    "merchant": {
      "id": 1,
      "name": "小米官方旗舰店",
      "rating": 4.9,
      "totalSales": 10000
    },
    "category": {
      "id": 3,
      "name": "小米手机",
      "parentCategory": {
        "id": 2,
        "name": "手机"
      }
    },
    "createdTime": "2024-01-01 10:00:00"
  }
}
```

### 5.3 商家添加商品
**POST** `/merchant/products`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "name": "string, required, 商品名称",
  "description": "string, optional, 商品描述",
  "price": "number, required, 价格",
  "stockQuantity": "number, required, 库存",
  "categoryId": "number, required, 分类ID",
  "sku": "string, required, SKU",
  "imageUrl": "string, optional, 图片URL"
}
```

### 5.4 商家更新商品
**PUT** `/merchant/products/{productId}`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

### 5.5 商家上下架商品
**PUT** `/merchant/products/{productId}/status`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "isActive": "number, required, 0-下架 1-上架"
}
```

## 6. 购物车管理

### 6.1 获取购物车
**GET** `/cart`

**Headers:**
```
Authorization: Bearer {token}
```

**响应:**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "userId": 1,
    "items": [
      {
        "id": 1,
        "productId": 1,
        "productName": "小米14 Pro",
        "productPrice": 4999.00,
        "quantity": 2,
        "imageUrl": "https://example.com/xiaomi14pro.jpg",
        "stockQuantity": 100,
        "subtotal": 9998.00
      }
    ],
    "totalAmount": 9998.00,
    "totalQuantity": 2
  }
}
```

### 6.2 添加商品到购物车
**POST** `/cart/items`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "productId": "number, required, 商品ID",
  "quantity": "number, required, 数量"
}
```

### 6.3 更新购物车商品数量
**PUT** `/cart/items/{itemId}`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "quantity": "number, required, 数量"
}
```

### 6.4 删除购物车商品
**DELETE** `/cart/items/{itemId}`

**Headers:**
```
Authorization: Bearer {token}
```

### 6.5 清空购物车
**DELETE** `/cart/items`

**Headers:**
```
Authorization: Bearer {token}
```

## 7. 订单管理

### 7.1 创建订单
**POST** `/orders`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "shippingAddressId": "number, required, 收货地址ID",
  "billingAddressId": "number, required, 账单地址ID",
  "paymentMethod": "string, required, 支付方式",
  "shippingMethod": "string, optional, 配送方式",
  "items": [
    {
      "productId": 1,
      "quantity": 1
    }
  ],
  "remark": "string, optional, 订单备注"
}
```

**响应:**
```json
{
  "code": 200,
  "data": {
    "orderId": 1,
    "orderNumber": "ORDER202401010001",
    "totalAmount": 4999.00,
    "paymentInfo": {
      "paymentMethod": "alipay",
      "paymentAmount": 4999.00
    }
  }
}
```

### 7.2 获取订单列表
**GET** `/orders`

**Headers:**
```
Authorization: Bearer {token}
```

**查询参数:**
- `status`: string, optional, 订单状态
- `page`: number, optional, 页码
- `size`: number, optional, 每页大小

**响应:**
```json
{
  "code": 200,
  "data": {
    "list": [
      {
        "id": 1,
        "orderNumber": "ORDER202401010001",
        "totalAmount": 4999.00,
        "status": "pending",
        "paymentStatus": "pending",
        "orderedTime": "2024-01-01 10:00:00",
        "items": [
          {
            "productName": "小米14 Pro",
            "productPrice": 4999.00,
            "quantity": 1,
            "imageUrl": "https://example.com/xiaomi14pro.jpg"
          }
        ]
      }
    ],
    "pagination": {
      "page": 1,
      "size": 10,
      "total": 5,
      "pages": 1
    }
  }
}
```

### 7.3 获取订单详情
**GET** `/orders/{orderId}`

**Headers:**
```
Authorization: Bearer {token}
```

**响应:**
```json
{
  "code": 200,
  "data": {
    "id": 1,
    "orderNumber": "ORDER202401010001",
    "totalAmount": 4999.00,
    "status": "pending",
    "paymentMethod": "alipay",
    "paymentStatus": "pending",
    "shippingMethod": "顺丰快递",
    "trackingNumber": null,
    "orderedTime": "2024-01-01 10:00:00",
    "paidTime": null,
    "shippedTime": null,
    "deliveredTime": null,
    "shippingAddress": {
      "recipient": "张三",
      "phone": "13800138000",
      "fullAddress": "北京市朝阳区建国路100号"
    },
    "billingAddress": {
      "recipient": "张三",
      "phone": "13800138000",
      "fullAddress": "北京市朝阳区建国路100号"
    },
    "items": [
      {
        "productId": 1,
        "productName": "小米14 Pro",
        "productPrice": 4999.00,
        "quantity": 1,
        "subtotal": 4999.00,
        "imageUrl": "https://example.com/xiaomi14pro.jpg"
      }
    ],
    "merchant": {
      "id": 1,
      "name": "小米官方旗舰店"
    }
  }
}
```

### 7.4 取消订单
**PUT** `/orders/{orderId}/cancel`

**Headers:**
```
Authorization: Bearer {token}
```

### 7.5 确认收货
**PUT** `/orders/{orderId}/confirm`

**Headers:**
```
Authorization: Bearer {token}
```

## 8. 支付管理

### 8.1 发起支付
**POST** `/payments`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "orderId": "number, required, 订单ID",
  "paymentMethod": "string, required, 支付方式"
}
```

**响应:**
```json
{
  "code": 200,
  "data": {
    "paymentId": 1,
    "paymentNumber": "PAY202401010001",
    "orderId": 1,
    "amount": 4999.00,
    "paymentMethod": "alipay",
    "paymentUrl": "https://alipay.com/pay/xxx",
    "qrCode": "data:image/png;base64,..."
  }
}
```

### 8.2 查询支付状态
**GET** `/payments/{paymentNumber}`

**Headers:**
```
Authorization: Bearer {token}
```

### 8.3 支付回调
**POST** `/payments/callback/{paymentMethod}`

**请求参数:** 依赖第三方支付平台

## 9. 商家管理

### 9.1 商家注册
**POST** `/merchant/register`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "name": "string, required, 商家名称",
  "description": "string, optional, 商家描述",
  "contactPerson": "string, required, 联系人",
  "contactPhone": "string, required, 联系电话",
  "email": "string, optional, 邮箱",
  "address": "string, optional, 商家地址",
  "businessLicense": "string, optional, 营业执照号",
  "merchantType": "number, required, 商家类型"
}
```

### 9.2 获取商家信息
**GET** `/merchant/profile`

**Headers:**
```
Authorization: Bearer {token}
```

### 9.3 更新商家信息
**PUT** `/merchant/profile`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

## 10. 库存管理

### 10.1 获取库存记录
**GET** `/merchant/inventory-logs`

**Headers:**
```
Authorization: Bearer {token}
```

**查询参数:**
- `productId`: number, optional, 商品ID
- `changeType`: string, optional, 变更类型
- `startDate`: string, optional, 开始日期
- `endDate`: string, optional, 结束日期

### 10.2 调整库存
**POST** `/merchant/inventory/adjust`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**请求参数:**
```json
{
  "productId": "number, required, 商品ID",
  "changeQuantity": "number, required, 变更数量",
  "reason": "string, required, 变更原因"
}
```

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 请求参数错误 |
| 401 | 未授权 |
| 403 | 禁止访问 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |
| 1001 | 用户已存在 |
| 1002 | 用户名或密码错误 |
| 1003 | Token已过期 |
| 2001 | 商品不存在 |
| 2002 | 商品库存不足 |
| 3001 | 订单不存在 |
| 3002 | 订单状态不允许此操作 |
| 4001 | 支付失败 |

## 注意事项

1. 所有请求头需要包含 `Content-Type: application/json`
2. 需要认证的接口必须在请求头中包含 `Authorization: Bearer {token}`
3. 时间格式统一使用 ISO 8601: `YYYY-MM-DD HH:mm:ss`
4. 金额单位统一为元，保留两位小数
5. 分页参数默认值: page=1, size=20
6. 所有列表接口都支持分页查询

## 接口版本

当前版本: v1  
历史版本: 无  
更新日期: 2024-01-01