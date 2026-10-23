package com.zhilan.zhilanserver.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 秒杀优惠券
 */
@Data
@TableName("seckill_voucher")
public class SeckillVoucher implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属商家 */
    private Long shopId;

    private String title;

    private String subTitle;

    /** 库存 */
    private Integer stock;

    /** 秒杀开始时间 */
    private LocalDateTime beginTime;

    /** 秒杀结束时间 */
    private LocalDateTime endTime;

    /** 支付金额（分） */
    private Long payValue;

    /** 抵扣金额（分） */
    private Long actualValue;

    /** 状态 1上架 0下架 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
