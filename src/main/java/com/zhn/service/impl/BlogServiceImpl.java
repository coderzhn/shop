package com.zhn.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhn.dto.Result;
import com.zhn.dto.ScrollResult;
import com.zhn.dto.UserDTO;
import com.zhn.entity.Blog;
import com.zhn.entity.Follow;
import com.zhn.entity.User;
import com.zhn.mapper.BlogMapper;
import com.zhn.service.IBlogService;
import com.zhn.service.IFollowService;
import com.zhn.service.IUserService;
import com.zhn.utils.SystemConstants;
import com.zhn.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zhn.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.zhn.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog ->{
            Long userId = blog.getUserId();
            User user = userService.getById(userId);
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(Long id) {
        Blog blog = getById(id);
        if(blog == null){
            return Result.fail("笔记不存在!");
        }
        queryBlogUser(blog);
        isBlogLiked(blog);
        return Result.ok(blog);
    }

    private void isBlogLiked(Blog blog) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //1.判断当前登录用户是否已经点赞
        String key = "blog:liked:"+ blog.getId();
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key,userId.toString());
        blog.setIsLike(BooleanUtil.isTrue(isMember));
    }

    @Override
    public Result likeBlog(Long id) {
        //获取登录用户
        Long userId = UserHolder.getUser().getId();
        //1.判断当前登录用户是否已经点赞
        String key = "blog:liked:"+id;
        Boolean isMember = stringRedisTemplate.opsForSet().isMember(key,userId.toString());
        if(BooleanUtil.isFalse(isMember)){
            //2.如果未点赞，可以点赞
            //2.1 数据库点赞数加一
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            //2.2 保存用户到Redis的set集合
            if (isSuccess){
                stringRedisTemplate.opsForSet().add(key,userId.toString());
            }
        }else {
            //3.如果已经点赞，取消点赞
            //3.1 数据库点赞数减一
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            //3.2 从redis中移除set
            stringRedisTemplate.opsForSet().remove(key,userId.toString());

        }
        return null;
    }

    public void queryBlogUser(Blog blog){
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
