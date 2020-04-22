package com.imooc.miaosha.domain;

import lombok.Data;

import java.util.Date;

@Data
public class OrderInfo {
	private Long id;
	private Long userId;
	private String userName;
	private Long goodsId;
	private Long  deliveryAddrId;
	private String goodsName;
	private Integer goodsCount;
	private Double goodsPrice;
	private Integer orderChannel;
	private Integer status;
	private Date createDate;
	private Date payDate;
	private String deliveryAddress;
}