package com.zhn.service.impl;

import cn.hutool.core.bean.BeanUtil;
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

}
