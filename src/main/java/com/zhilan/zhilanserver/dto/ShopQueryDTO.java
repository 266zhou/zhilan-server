package com.zhilan.zhilanserver.dto;

import lombok.Data;

/**
 * 商家分页查询条件
 */
@Data
public class ShopQueryDTO {

    /** 关键字：匹配名称/地址 */
    private String keyword;

    /** 类型 */
    private String type;

    /** 城市 */
    private String city;

    /** 状态 1营业 0停业 ，为空查全部 */
    private Integer status;

    /** 排序字段：score / sales / avgPrice */
    private String sortBy;

    /** 是否升序，默认降序 */
    private Boolean asc = false;

    private  Integer page = 1;

    private Integer pageSize = 10;

}