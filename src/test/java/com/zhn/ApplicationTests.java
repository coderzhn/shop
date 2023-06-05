package com.zhn;

import com.zhn.entity.Shop;
import com.zhn.service.impl.ShopServiceImpl;
import com.zhn.utils.CacheClient;
import com.zhn.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zhn.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.zhn.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class ApplicationTests {

    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private RedisIdWorker redisIdWorker;
    private ExecutorService es = Executors.newFixedThreadPool(500);
    @Test
    void testSaveShop() throws InterruptedException {
        shopService.saveShopToRedis(2l,10l);
    }
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        Runnable task = ()->{
          for (int i = 0; i < 100;i++){
              long id = redisIdWorker.nextId("order");
              System.out.println("id = "+id);
          }
          latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0;i < 300;i++){
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time:"+(end-begin));
    }
}
