package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;

/**
 * ClassName: IVoucherOrderService
 * Description:
 *
 * @Author Seth Neiman
 * @Create 2024/5/20 14:04
 * @Version 1.0
 */
public interface IVoucherOrderService {
    Result seckillVoucher(Long voucherId);

    void createVoucherOrder(VoucherOrder voucherOrder);
}
