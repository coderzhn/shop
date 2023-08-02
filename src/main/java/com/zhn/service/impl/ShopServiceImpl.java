package com.zhn.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhn.dto.Result;
import com.zhn.entity.Shop;
import com.zhn.mapper.ShopMapper;
import com.zhn.service.IShopService;
import com.zhn.utils.CacheClient;
import com.zhn.utils.RedisData;
import com.zhn.utils.RegexUtils;
import com.zhn.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.zhn.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {

        Shop shop = cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, id2 -> getById(id2), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //互斥锁解决缓存击穿
        //Shop shop = queryWithPassMutex(id);
        //逻辑过期解决缓存穿透问题
//        Shop shop = cacheClient.queryWithLogicalExpire(CACHE_SHOP_KEY,id,Shop.class,id2->getById(id2),CACHE_SHOP_TTL,TimeUnit.MINUTES);
        return Result.ok(shop);
    }
    public Shop queryWithPassMutex(Long id){
        String key = CACHE_SHOP_KEY + id;
        //1.从redis查询商铺缓存
        String lockKey = null;
        Shop shop = null;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断是否存在
        if(!StrUtil.isBlank((shopJson))){
            //3.存在，直接返回
            shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //为防止缓存穿透，判断是否命中空值
        if (shopJson!=null){ //不为NULL则为空字符串
            return null;
        }
        //实现缓存重建
        // 1.获取互斥锁
        lockKey = "lock:shop:"+id;
        try {
            boolean isLock = tryLock(lockKey);
            // 2.判断是否获取成功
            if(!isLock){
                // 3.失败，休眠重试
                Thread.sleep(50);
                return queryWithPassMutex(id);
            }
            // 4.成功 根据id查询数据库
            shop = getById(id);
            Thread.sleep(300);
            //5.不存在，返回错误
            if(shop == null){
                //防止缓存穿透将空值写入Redis
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //6.存在，写入redis中
            stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unLock(lockKey);
        }
        //8.返回
        return shop;
    }


//    public Shop queryWithPassThrough(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(!StrUtil.isBlank((shopJson))){
//            //3.存在，直接返回
//            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
//            return shop;
//        }
//        //为防止缓存穿透，判断是否命中空值
//        if (shopJson!=null){ //不为NULL则为空字符串
//            return null;
//        }
//        //4.不存在，根据id查询数据库
//        Shop shop = getById(id);
//        //5.不存在，返回错误
//        if(shop == null){
//            //防止缓存穿透将空值写入Redis
//            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
//            return null;
//        }
//        //6.存在，写入redis中
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7.返回
//        return shop;
//    }
    public void saveShopToRedis(Long id,Long expireSeconds) throws InterruptedException {
        //1.查询店铺数据
        Shop shop = getById(id);
        Thread.sleep(200);
        //2.封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(redisData));

    }
//    public Shop queryWithLogicalExpire(Long id){
//        String key = CACHE_SHOP_KEY + id;
//        //1.从redis查询商铺缓存
//        String shopJson = stringRedisTemplate.opsForValue().get(key);
//        //2.判断是否存在
//        if(StrUtil.isBlank((shopJson))){
//            //3.不存在，直接空
//            return null;
//        }
//        //4.命中，json反序列化为对象
//        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
//        Shop shop =  JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
//        LocalDateTime expireTime = redisData.getExpireTime();
//        //5.判断是否过期
//        if(expireTime.isAfter(LocalDateTime.now()))
//            //5.1 未过期，直接返回店铺信息
//            return shop;
//            //5.2 过期，需要进行缓存重建
//        //6.缓存重建
//            //6.1 获取互斥锁
//        boolean flag = tryLock(LOCK_SHOP_KEY+id);
//        //6.2 判断是否获取成功
//        if(flag){
//            //6.3 成功，开启独立线程
//            CACHE_REBUILD_EXECUTOR.submit(()->{
//                try {
//                    //重建缓存
//                    this.saveShopToRedis(id,20l);
//                } catch (Exception e) {
//                    throw new RuntimeException(e);
//                } finally {
//                    //释放锁
//                    unLock(LOCK_SHOP_KEY+id);
//                }
//            });
//        }
//        else {
//            //6.4 失败直接返回，返回过期店铺信息
//            return shop;
//        }
//        shop = getById(id);
//        //6.存在，写入redis中
//        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(shop),CACHE_SHOP_TTL, TimeUnit.MINUTES);
//        //7.返回
//        return shop;
//    }
    @Override
    @Transactional //添加事务
    public Result update(Shop shop) {
        Long id = shop.getId();
        if(id == null){
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //1.是否需要根据坐标查询
        if(x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            return Result.ok(page.getRecords());
        }
        //2.计算分页参数
        String key = SHOP_GEO_KEY + typeId;
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        //3.查询redis，按照距离排序分页 结果：shopId、distance
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .search(
                        key,
                        GeoReference.fromCoordinate(x, y),
                        new Distance(5000),
                        RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().limit(end)
                );
        //4.解析出id
        if(results == null){
            return Result.ok();
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
        if(list.size() <= from){
            //没有下一页
            return Result.ok(Collections.emptyList());
        }
        Map<String,Distance> distanceMap = new HashMap<>(list.size());
        List<Long> ids = new ArrayList<>(list.size());
        //4.1 截取from到end的部分
        list.stream().skip(from).forEach(result -> {
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr,distance);
        });
        //5.根据id查询Shop
        String idsStr = StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list();
        for(Shop shop:shops){
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }
        //6.返回数据
        return Result.ok(shops);
    }

    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }
    private void unLock(String key){
            stringRedisTemplate.delete(key);
    }
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
}