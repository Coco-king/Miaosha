package com.imooc.miaosha.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.domain.AlipayTradePagePayModel;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.imooc.miaosha.access.AccessLimit;
import com.imooc.miaosha.alipay.AliPayConfigBean;
import com.imooc.miaosha.alipay.AlipayConfig;
import com.imooc.miaosha.alipay.AlipayUtils;
import com.imooc.miaosha.domain.MiaoshaUser;
import com.imooc.miaosha.domain.OrderInfo;
import com.imooc.miaosha.domain.OrderPay;
import com.imooc.miaosha.domain.PayInfo;
import com.imooc.miaosha.enums.Const;
import com.imooc.miaosha.service.AlipayService;
import com.imooc.miaosha.service.MiaoshaUserService;
import com.imooc.miaosha.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

@Controller
@RequestMapping("/alipay")
@Slf4j
public class AlipayController extends AlipayConfigController {


    @Autowired
    OrderService orderService;

    @Autowired
    AliPayConfigBean aliPayConfigBean;

    @Autowired
    MiaoshaUserService miaoshaUserService;

    @Autowired
    AlipayService alipayService;

    @Override
    public AlipayConfig getAliPayConfig() {
        return AlipayConfig.New().setConfigProperties(aliPayConfigBean).build();
    }

    @RequestMapping(value = "/pcpay", method = RequestMethod.GET)
    @ResponseBody
    @AccessLimit(seconds = 5, maxCount = 5, needLogin = true)
    public void startPay(HttpServletResponse response, @RequestParam("orderId") Long orderId) {
       // Long orderId = Long.valueOf("13");
        OrderInfo orderInfo = null;
        if (orderId != null) {
            orderInfo = orderService.getOrderById(orderId);
        }
        //create pay order and insert into DB

        try {
            //???????????????
            String outTradeNo = getOutTradeNo();
            //????????????
            String totalAmount = orderInfo.getGoodsPrice().toString();

            log.info("pcPay outTradeNo--->" + outTradeNo);
            //????????????
            String returnUrl = aliPayConfigBean.getDomain() + "/alipay/return_url";
            //??????????????????
            String notifyUrl = aliPayConfigBean.getDomain() + "/alipay/notify_url";

            String subject = orderInfo.getGoodsName();
            String body = orderInfo.getDeliveryAddress();
            AlipayTradePagePayModel model = new AlipayTradePagePayModel();
            model.setOutTradeNo(outTradeNo);
            model.setProductCode("FAST_INSTANT_TRADE_PAY");
            model.setTotalAmount(totalAmount);
            model.setSubject(subject);
            model.setBody(body);
            model.setPassbackParams("passback_params");
            OrderPay orderPay = alipayService.createPayOrder(orderInfo, model,aliPayConfigBean);

            AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
            request.setBizModel(model);
            request.setNotifyUrl(notifyUrl);
            request.setReturnUrl(returnUrl);
            AlipayUtils.tradePayPage(response, request);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @RequestMapping(value = "/return_url")
    @ResponseBody
    public ModelAndView return_url(HttpServletRequest request, Map<String, Object> map) {
        Map<String, String> params = AlipayUtils.toMap(request);
        OrderPay orderPay = alipayService.getOrderByOutTradeNo(params.get("out_trade_no"));
        MiaoshaUser user = miaoshaUserService.getById(orderPay.getUserId());
        //???????????????????????????????????????????????????
        orderPay.setTradeStatus(Const.AlipayCallback.TRADE_STATUS_TRADE_SUCCESS);
        TimeZone tz = TimeZone.getTimeZone("ETC/GMT-8");
        TimeZone.setDefault(tz);
        orderPay.setGmtPayment(new Date());
        alipayService.setOrderByOutTradeNo(orderPay);
        OrderInfo orderInfo = orderService.getOrderById(orderPay.getOrderId());
        orderInfo.setStatus(1);
        orderInfo.setPayDate(orderPay.getGmtPayment());
        orderService.updateOrderInfoById(orderInfo);
        PayInfo payInfo = new PayInfo();
        payInfo.setCreateTime(orderPay.getGmtCreate());
        payInfo.setOrderId(orderPay.getOrderId());
        payInfo.setUserId(orderPay.getUserId());
        payInfo.setPayPlatform(Const.PayPlatformEnum.ALIPAY.getValue());
        payInfo.setPlatformNumber(Const.PayPlatformEnum.ALIPAY.getCode());
        payInfo.setPlatformStatus(Const.PaymentTypeEnum.ONLINE_PAY.getValue());
        payInfo.setUpdateTime(orderPay.getGmtPayment());
        alipayService.insertPayInfo(payInfo);
        map.put("payInfo",payInfo);
        map.put("orderPay",orderPay);
        map.put("user",user);
        return new ModelAndView("orderpay_detail", map);
    }

//    @RequestMapping(value = "/return_url")
//    public String return_url(HttpServletRequest request) {
//        Map<String, String> params = AlipayUtils.toMap(request);
//        OrderPay orderPay = alipayService.getOrderByOutTradeNo(params.get("out_trade_no"));
//        OrderPayVo orderPayVo = new OrderPayVo();
//        orderPayVo.setOrderPay(orderPay);
//        SeckillUser seckillUser =seckillUserService.getById(orderPay.getUserId());
//        //return Result.success(orderPayVo);
//        return "redirect:/order/detail?orderId="+orderPay.getOrderId();
//    }

    @RequestMapping(value = "/notify_url")
    @ResponseBody
    public String notify_url(HttpServletRequest request) {
        Map<String, String> params = AlipayUtils.toMap(request);
        params.remove("sign_type");
        try {
            // ???????????????GET??????????????????
            boolean verify_result = AlipaySignature.rsaCheckV2(params,aliPayConfigBean.getPublicKey(),aliPayConfigBean.getCHARSET(),
                    aliPayConfigBean.getSIGNTYPE());

            if (verify_result) {// ????????????
                // TODO ??????????????????????????????????????????????????? ?????????????????????????????????????????? ?????????????????????
                System.out.println("notify_url ????????????succcess");
                return "success";
            } else {
                System.out.println("notify_url ????????????");
                // TODO
                return "failure";
            }
        } catch (AlipayApiException e) {
            e.printStackTrace();
            return "failure";
        }
    }

    public static String getOutTradeNo() {
        SimpleDateFormat format = new SimpleDateFormat("MMddHHmmss", Locale.getDefault());
        Date date = new Date();
        String key = format.format(date);
        key = key + System.currentTimeMillis();
        key = key.substring(0, 15);
        return key;
    }

}
