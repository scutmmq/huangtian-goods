-- MySQL dump 10.13  Distrib 8.0.20, for Win64 (x86_64)
--
-- Host: localhost    Database: online_mall
-- ------------------------------------------------------
-- Server version	8.0.20

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `online_mall` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `online_mall`;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
                        `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
                        `username` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '账号',
                        `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码',
                        `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
                        `gender` tinyint DEFAULT '1' COMMENT '性别: 0-女, 1-男, 2-其他',
                        `birthday` date DEFAULT NULL COMMENT '生日',
                        `nick_name` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '昵称,默认随机生成',
                        `phone` varchar(11) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '电话号码',
                        `image` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '头像URL',
                        `address` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '地址',
                        `is_active` tinyint DEFAULT '1' COMMENT '是否激活: 0-禁用, 1-激活',
                        `last_login` datetime DEFAULT NULL COMMENT '最后登录时间',
                        `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `username` (`username`),
                        UNIQUE KEY `phone` (`phone`),
                        UNIQUE KEY `email` (`email`),
                        INDEX `idx_username` (`username`),
                        INDEX `idx_phone` (`phone`),
                        INDEX `idx_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `user_address`
--

DROP TABLE IF EXISTS `user_address`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_address` (
                                `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '地址ID',
                                `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
                                `recipient` varchar(50) NOT NULL COMMENT '收件人',
                                `phone` varchar(20) NOT NULL COMMENT '收件电话',
                                `province` varchar(20) NOT NULL COMMENT '省份',
                                `city` varchar(20) NOT NULL COMMENT '城市',
                                `district` varchar(20) DEFAULT '' COMMENT '区县',
                                `detail` varchar(200) NOT NULL COMMENT '详细地址',
                                `postal_code` varchar(10) DEFAULT '' COMMENT '邮政编码',
                                `is_default` tinyint DEFAULT '0' COMMENT '是否默认地址',
                                `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                PRIMARY KEY (`id`),
                                KEY `idx_user_id` (`user_id`),
                                CONSTRAINT `fk_user_address_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户地址表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant`
--

DROP TABLE IF EXISTS `merchant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant` (
                            `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '商家ID',
                            `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商家名称',
                            `description` text COLLATE utf8mb4_unicode_ci COMMENT '商家描述',
                            `logo_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '商家Logo',
                            `contact_person` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人',
                            `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系电话',
                            `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
                            `address` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '商家地址',
                            `business_license` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '营业执照号',
                            `merchant_type` tinyint DEFAULT '1' COMMENT '商家类型: 1-个人, 2-企业',
                            `status` tinyint DEFAULT '0' COMMENT '状态: 0-待审核, 1-正常, 2-禁用, 3-审核失败',
                            `rating` decimal(3,2) DEFAULT '5.00' COMMENT '商家评分',
                            `total_sales` int DEFAULT '0' COMMENT '总销量',
                            `is_active` tinyint DEFAULT '1' COMMENT '是否激活',
                            `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`id`),
                            UNIQUE KEY `uk_name` (`name`),
                            KEY `idx_status` (`status`),
                            KEY `idx_rating` (`rating`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `merchant_user`
--

DROP TABLE IF EXISTS `merchant_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `merchant_user` (
                                 `id` bigint unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                 `merchant_id` bigint unsigned NOT NULL COMMENT '商家ID',
                                 `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
                                 `role` enum('owner','manager','staff') NOT NULL DEFAULT 'staff' COMMENT '角色',
                                 `permissions` JSON COMMENT '权限配置',
                                 `is_active` tinyint DEFAULT '1' COMMENT '是否激活',
                                 `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                 `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                 UNIQUE KEY `uk_merchant_user` (`merchant_id`, `user_id`),
                                 FOREIGN KEY (`merchant_id`) REFERENCES `merchant`(`id`),
                                 FOREIGN KEY (`user_id`) REFERENCES `user`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商家用户关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product_category`
--

DROP TABLE IF EXISTS `product_category`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category` (
                                    `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '分类ID',
                                    `name` varchar(100) NOT NULL COMMENT '分类名称',
                                    `parent_id` bigint NOT NULL DEFAULT '0' COMMENT '父分类ID（0表示一级分类）',
                                    `level` tinyint NOT NULL COMMENT '分类级别（1：一级，2：二级，3：三级）',
                                    `sort` int NOT NULL DEFAULT '0' COMMENT '排序（数值越小越靠前）',
                                    `icon` varchar(255) DEFAULT NULL COMMENT '分类图标URL',
                                    `description` varchar(500) DEFAULT NULL COMMENT '分类描述',
                                    `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态（0：禁用，1：启用）',
                                    `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    PRIMARY KEY (`id`),
                                    KEY `idx_parent_id` (`parent_id`) COMMENT '父分类索引，用于查询子分类',
                                    KEY `idx_status_level` (`status`,`level`) COMMENT '状态+级别索引，用于筛选有效分类'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `product`
--

DROP TABLE IF EXISTS `product`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product` (
                           `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '商品ID',
                           `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品名称',
                           `description` text COLLATE utf8mb4_unicode_ci COMMENT '商品描述',
                           `price` decimal(10,2) NOT NULL COMMENT '价格',
                           `stock_quantity` int DEFAULT '0' COMMENT '库存数量',
                           `category_id` bigint unsigned NOT NULL COMMENT '分类ID',
                           `sku` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '商品SKU',
                           `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT '' COMMENT '商品图片URL',
                           `is_active` tinyint DEFAULT '1' COMMENT '是否上架: 0-下架, 1-上架',
                           `merchant_id` bigint unsigned NOT NULL COMMENT '商家ID',
                           `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           PRIMARY KEY (`id`),
                           UNIQUE KEY `sku` (`sku`),
                           KEY `idx_category_id` (`category_id`),
                           KEY `idx_merchant_id` (`merchant_id`),
                           KEY `idx_sku` (`sku`),
                           KEY `idx_is_active` (`is_active`),
                           KEY `idx_created_at` (`created_at`),
                           CONSTRAINT `fk_product_category` FOREIGN KEY (`category_id`) REFERENCES `product_category` (`id`),
                           CONSTRAINT `fk_product_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `inventory_logs`
--

DROP TABLE IF EXISTS `inventory_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `inventory_logs` (
                                  `id` bigint unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                  `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
                                  `change_type` enum('in','out','adjust') NOT NULL COMMENT '变更类型',
                                  `change_quantity` int NOT NULL COMMENT '变更数量',
                                  `current_quantity` int NOT NULL COMMENT '变更后库存',
                                  `reason` varchar(200) COMMENT '变更原因',
                                  `reference_id` bigint unsigned COMMENT '关联ID（订单ID等）',
                                  `operator_id` bigint unsigned COMMENT '操作人ID',
                                  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                  FOREIGN KEY (`product_id`) REFERENCES `product`(`id`),
                                  KEY `idx_product_id` (`product_id`),
                                  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='库存变更记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `carts`
--

DROP TABLE IF EXISTS `carts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `carts` (
                         `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '购物车ID',
                         `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
                         `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_user_id` (`user_id`),
                         CONSTRAINT `fk_carts_user_id` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `cart_items`
--

DROP TABLE IF EXISTS `cart_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cart_items` (
                              `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '购物车项ID',
                              `cart_id` bigint unsigned NOT NULL COMMENT '购物车ID',
                              `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
                              `quantity` int NOT NULL DEFAULT '1' COMMENT '商品数量',
                              `created_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `updated_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              PRIMARY KEY (`id`),
                              KEY `fk_cart_cart_imtems_id` (`cart_id`),
                              KEY `fk_cart_imtems_product_id` (`product_id`),
                              CONSTRAINT `fk_cart_cart_imtems_id` FOREIGN KEY (`cart_id`) REFERENCES `carts` (`id`) ON DELETE CASCADE,
                              CONSTRAINT `fk_cart_imtems_product_id` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `orders`
--

DROP TABLE IF EXISTS `orders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `orders` (
                          `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '订单ID',
                          `order_number` varchar(50) NOT NULL COMMENT '订单号（唯一标识）',
                          `user_id` bigint unsigned NOT NULL COMMENT '用户ID',
                          `merchant_id` bigint unsigned NOT NULL COMMENT '商家ID',
                          `total_amount` decimal(10,2) NOT NULL COMMENT '订单总金额',
                          `status` enum('pending','paid','shipped','delivered','cancelled') NOT NULL DEFAULT 'pending' COMMENT '订单状态：pending-待支付，paid-已支付，shipped-已发货，delivered-已送达，cancelled-已取消',
                          `payment_method` varchar(50) NOT NULL COMMENT '支付方式（如支付宝、微信）',
                          `payment_status` enum('pending','paid','failed','refunded') NOT NULL DEFAULT 'pending' COMMENT '支付状态：pending-待支付，paid-已支付，failed-支付失败，refunded-已退款',
                          `shipping_method` varchar(50) DEFAULT NULL COMMENT '配送方式（如顺丰、京东物流）',
                          `tracking_number` varchar(100) DEFAULT NULL COMMENT '物流单号',
                          `ordered_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下单时间',
                          `paid_at` timestamp NULL DEFAULT NULL COMMENT '支付时间',
                          `shipped_at` timestamp NULL DEFAULT NULL COMMENT '发货时间',
                          `delivered_at` timestamp NULL DEFAULT NULL COMMENT '送达时间',
                          `shipping_address_id` bigint unsigned NOT NULL COMMENT '收货地址ID',
                          `billing_address_id` bigint unsigned NOT NULL COMMENT '账单地址ID',
                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_order_number` (`order_number`),
                          KEY `idx_user_id` (`user_id`),
                          KEY `idx_merchant_id` (`merchant_id`),
                          KEY `idx_status` (`status`),
                          KEY `idx_payment_status` (`payment_status`),
                          KEY `shipping_address_id` (`shipping_address_id`),
                          KEY `billing_address_id` (`billing_address_id`),
                          CONSTRAINT `fk_order_user` FOREIGN KEY (`user_id`) REFERENCES `user` (`id`) ON DELETE RESTRICT,
                          CONSTRAINT `fk_order_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
                          CONSTRAINT `fk_order_shipping_address` FOREIGN KEY (`shipping_address_id`) REFERENCES `user_address` (`id`),
                          CONSTRAINT `fk_order_billing_address` FOREIGN KEY (`billing_address_id`) REFERENCES `user_address` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `order_items`
--

DROP TABLE IF EXISTS `order_items`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `order_items` (
                               `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '订单项ID',
                               `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
                               `product_id` bigint unsigned NOT NULL COMMENT '商品ID',
                               `product_name` varchar(200) NOT NULL COMMENT '商品名称（下单时快照）',
                               `product_price` decimal(10,2) NOT NULL COMMENT '商品单价（下单时快照）',
                               `quantity` int NOT NULL COMMENT '购买数量',
                               `subtotal` decimal(10,2) NOT NULL COMMENT '小计金额（单价×数量）',
                               `merchant_id` bigint unsigned NOT NULL COMMENT '商家ID（快照）',
                               PRIMARY KEY (`id`),
                               KEY `idx_order_id` (`order_id`),
                               KEY `idx_product_id` (`product_id`),
                               KEY `idx_merchant_id` (`merchant_id`),
                               CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`) ON DELETE CASCADE,
                               CONSTRAINT `fk_order_item_product` FOREIGN KEY (`product_id`) REFERENCES `product` (`id`) ON DELETE RESTRICT,
                               CONSTRAINT `fk_order_item_merchant` FOREIGN KEY (`merchant_id`) REFERENCES `merchant` (`id`),
                               CONSTRAINT `ck_subtotal` CHECK ((`subtotal` = (`product_price` * `quantity`)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单项表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Table structure for table `payment_records`
--

DROP TABLE IF EXISTS `payment_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `payment_records` (
                                   `id` bigint unsigned NOT NULL AUTO_INCREMENT PRIMARY KEY,
                                   `order_id` bigint unsigned NOT NULL COMMENT '订单ID',
                                   `payment_number` varchar(50) NOT NULL UNIQUE COMMENT '支付流水号',
                                   `amount` decimal(10,2) NOT NULL COMMENT '支付金额',
                                   `payment_method` varchar(50) NOT NULL COMMENT '支付方式',
                                   `payment_status` enum('pending','success','failed','refunded') DEFAULT 'pending',
                                   `transaction_id` varchar(100) COMMENT '第三方交易ID',
                                   `paid_at` datetime COMMENT '支付时间',
                                   `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
                                   FOREIGN KEY (`order_id`) REFERENCES `orders`(`id`),
                                   INDEX `idx_payment_number` (`payment_number`),
                                   INDEX `idx_transaction_id` (`transaction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `product_category`
--

LOCK TABLES `product_category` WRITE;
/*!40000 ALTER TABLE `product_category` DISABLE KEYS */;
INSERT INTO `product_category` VALUES
                                   (1,'电子产品',0,1,1,'/icons/electronic.png','手机、电脑等数码产品',1,'2025-11-09 22:29:51','2025-11-09 22:29:51'),
                                   (2,'手机',1,2,1,'/icons/phone.png',NULL,1,'2025-11-09 22:29:53','2025-11-09 22:29:53'),
                                   (3,'小米手机',2,3,1,NULL,NULL,1,'2025-11-09 22:29:55','2025-11-09 22:29:55');
/*!40000 ALTER TABLE `product_category` ENABLE KEYS */;
UNLOCK TABLES;

/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;