package com.zhilan.zhilanserver.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import lombok.Data;


import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 优惠券订单
 */
@Data
@TableName("voucher_order")
public class VoucherOrder implements Serializable {

    /** 订单有效标志 */
   public static final int ACTIVE_FLAG_VALID = 1;

    /** 订单状态常量 */
    public static final int STATUS_UNPAID = 1;     //未支付
    public static final int STATUS_PAID = 2;     //已支付
    public static final int STATUS_CANCELED = 3;     //已取消
    public static final int STATUS_REFUNDED = 4;     //已退款

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 下单用户 */
    private Long userId;

    /** 优惠券 id */
    private Long voucherId;

    /** 状态 1未支付 2已支付 3已取消 4已退款 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    private LocalDateTime payTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 乐观锁版本号 */
    @Version
    private Integer version;

    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Integer activeFlag;
}
