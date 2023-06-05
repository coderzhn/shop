package com.zhn.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zhn.dto.Result;
import com.zhn.entity.ShopType;
import com.zhn.mapper.ShopTypeMapper;
import com.zhn.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.zhn.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result getTypeList() {
        // 1.从 Redis 中查询商铺缓存
        String shopTypeJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_TYPE_KEY);
        // 2.判断 Redis 中是否存在数据
        if (StrUtil.isNotBlank(shopTypeJson)) {
            // 2.1.存在，则返回
            List<ShopType> shopTypes = JSONUtil.toList(shopTypeJson, ShopType.class);
            return Result.ok(shopTypes);
        }
        // 2.2.Redis 中不存在，则从数据库中查询
        List<ShopType> shopTypes = query().orderByAsc("sort").list();

        // 3.判断数据库中是否存在
        if (shopTypes == null) {
            // 3.1.数据库中也不存在，则返回 false
            return Result.fail("分类不存在！");
        }
        // 3.2.数据库中存在，则将查询到的信息存入 Redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_TYPE_KEY, JSONUtil.toJsonStr(shopTypes));
        // 3.3返回
        return Result.ok(shopTypes);
    }
}
