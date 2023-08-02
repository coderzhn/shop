package com.zhn.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhn.dto.Result;
import com.zhn.dto.UserDTO;
import com.zhn.entity.Follow;
import com.zhn.mapper.FollowMapper;
import com.zhn.service.IFollowService;
import com.zhn.service.IUserService;
import com.zhn.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    IUserService userService;
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        String key = "follows:" + userId;
        //1.判断关注还是取关
        if(isFollow){
            //2.关注，新增数据
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if(isSuccess){
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }
        }else {
            remove(new QueryWrapper<Follow>().eq("user_id",userId)
                    .eq("follow_user_id",followUserId));
            stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
        }
        //3.取关，删除
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取用户
        Long userId = UserHolder.getUser().getId();
        Integer count = query().eq("user_id", userId)
                .eq("fsollow_user_id", followUserId).count();
        return Result.ok(count>0);
    }

    @Override
    public Result followCommons(Long id) {
        //1.获取当前用户
        Long userId = UserHolder.getUser().getId();
        String key1 = "follows:" + userId;
        String key2 = "follows:" + id;
        //2.求交集
        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if(intersect == null || intersect.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //3.解析id集合
        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> users = userService.listByIds(ids)
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        return Result.ok(users);
    }
}
