package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {
    /**
     * 生成验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendcode(String phone);

    /**
     * 用户登录校验
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm);
}
