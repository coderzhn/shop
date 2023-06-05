package com.zhn.utils;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = false)
public class RedisData {
    private LocalDateTime expireTime;
    private Object data;

    public void setExpireTime(LocalDateTime expireTime) {
        this.expireTime = expireTime;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
