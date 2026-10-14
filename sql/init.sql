-- ============================================================
-- 知览本地生活服务平台 - 初始化脚本（阶段1）
-- 执行方式：mysql -uroot -p < init.sql  或  用 Navicat/IDEA 执行
-- ============================================================

CREATE DATABASE IF NOT EXISTS `zhilan` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE `zhilan`;

-- 商家表
DROP TABLE IF EXISTS `shop`;
CREATE TABLE `shop` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    `name`           VARCHAR(64)  NOT NULL COMMENT '商家名称',
    `type`           VARCHAR(32)  NOT NULL COMMENT '类型：美食/休闲娱乐/酒店民宿/丽人/运动健身/生活服务',
    `city`           VARCHAR(32)  DEFAULT NULL COMMENT '城市',
    `address`        VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
    `phone`          VARCHAR(20)  DEFAULT NULL COMMENT '联系电话',
    `business_hours` VARCHAR(64)  DEFAULT NULL COMMENT '营业时间',
    `avg_price`      DECIMAL(10,2) DEFAULT NULL COMMENT '人均价格',
    `score`          DECIMAL(2,1) DEFAULT 5.0 COMMENT '评分(0-5)',
    `sales`          INT          DEFAULT 0 COMMENT '销量',
    `comments`       INT          DEFAULT 0 COMMENT '评论数',
    `images`         VARCHAR(1024) DEFAULT NULL COMMENT '图片URL，逗号分隔',
    `description`    TEXT COMMENT '简介',
    `status`         TINYINT      DEFAULT 1 COMMENT '状态 1营业 0停业',
    `deleted`        TINYINT      DEFAULT 0 COMMENT '逻辑删除 0未删 1已删',
    `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_type` (`type`),
    KEY `idx_city` (`city`),
    KEY `idx_name` (`name`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商家表';

-- 示例数据
INSERT INTO `shop` (`name`, `type`, `city`, `address`, `phone`, `business_hours`, `avg_price`, `score`, `sales`, `comments`, `description`) VALUES
('老王火锅',   '美食',   '杭州', '西湖区文三路100号',  '0571-88880001', '10:00-22:00', 98,  4.8, 1200, 320, '地道川味火锅，麻辣鲜香'),
('星聚咖啡',   '美食',   '杭州', '上城区延安路88号',  '0571-88880002', '08:00-20:00', 35,  4.5, 2100, 500, '精品手冲咖啡与轻食'),
('悦心足道',   '休闲娱乐', '杭州', '拱墅区莫干山路66号', '0571-88880003', '12:00-02:00', 168, 4.6, 800,  150, '中式足疗按摩SPA'),
('云朵酒店',   '酒店民宿', '杭州', '滨江区江南大道500号', '0571-88880004', '24小时',    320, 4.7, 950,  260, '江景商务酒店'),
('美发沙龙',   '丽人',   '杭州', '萧山区市心路20号',  '0571-88880005', '09:00-21:00', 120, 4.4, 600,  90,  '日系造型设计');
