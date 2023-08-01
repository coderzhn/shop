package com.zhn.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {
    private StringRedisTemplate stringRedisTemplate;
    //定义业务名称
    private String name;
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }
    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }
    private static final String KEY_PREFIX = "lock:";

    private static final String ID_PREFIX = UUID.randomUUID().toString(true)+"-";
    String key = KEY_PREFIX+name;
    @Override
    public boolean tryLock(long timeoutSec) {
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key,threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success); //防止自动拆箱产生空指针风险
    }

    @Override
    public void unlock() {
        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(key),
                ID_PREFIX + Thread.currentThread().getId());
    }
//    @Override
//    public void unlock() {
//        //获取线程标示
//        String threadId = ID_PREFIX + Thread.currentThread().getId();
//        //获取锁中的标示
//        String id = stringRedisTemplate.opsForValue().get(key);
//        //判断标示是否一致
//        if(threadId.equals(id)){
//            //释放锁
//            stringRedisTemplate.delete(key);
//        }
//    }
}
