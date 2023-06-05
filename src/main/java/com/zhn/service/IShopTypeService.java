package com.zhn.service;

import com.zhn.dto.Result;
import com.zhn.entity.ShopType;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
public interface IShopTypeService extends IService<ShopType> {

    Result getTypeList();
}
