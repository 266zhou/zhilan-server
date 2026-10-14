package com.zhilan.zhilanserver.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商家实体
 */
@Data
@TableName("shop")
public class Shop implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "商家名称不能为空")
    private String name;

    /** 类型：美食/休闲娱乐/酒店民宿/丽人/运动健身/生活服务 */
    @NotBlank(message = "商家类型不能为空")
    private String type;

    private String city;

    private String address;

    private String phone;

    private String businessHours;

    /** 人均价格 */
    private BigDecimal avgPrice;

    /** 评分 0-5 */
    private BigDecimal score;

    /** 销量 */
    private Integer sales;

    /** 评论数 */
    private Integer comments;

    /** 图片URL，逗号分隔 */
    private String images;

    private String description;
    /** 状态 1营业 0停业 */
    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}