package com.zhn.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhn.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
public interface VoucherMapper extends BaseMapper<Voucher> {

    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
