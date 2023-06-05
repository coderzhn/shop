package com.zhn.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhn.dto.LoginFormDTO;
import com.zhn.dto.Result;
import com.zhn.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 张浩楠
 * @since 2023-02-22
 */
public interface IUserService extends IService<User> {

    Result sendCode(String phone, HttpSession session);

    Result login(LoginFormDTO loginForm, HttpSession session);

    Result sign();

    Result signCount();

}
