package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 用redis解决分布式部署的session共享问题：生成验证码，存入redis
     * @param phone
     * @param session（redis方式实现不需要session参数）
     * @return
     */
    @Override
    public Result sendcode(String phone) {
        //1.校验手机号格式，正确继续发送验证码，不正确返回错误信息
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
        if (phoneInvalid)
        {
            return Result.fail("手机号格式不正确");
        }

        //获得一个只包含数字的字符串,length 字符串的长度.return 随机字符串(hutool包下的)
        //2.生成验证码，uuid截取6位
        String code = RandomUtil.randomNumbers(6);
        //3.保存验证码到redis中，用于登录校验，加上这个phone是为了防止A账号获取验证码，用B手机号登录（加上过期时间，必须在10分钟内登录）
        stringRedisTemplate.opsForValue().set("code:"+phone,code,10, TimeUnit.MINUTES);
        log.info("验证码是{}",code);
        //4.返回验证码
        return Result.ok(code);
    }

    /**
     * 用redis解决分布式部署的session共享问题：登陆成功后，将原来的user存入session，变为存入redis
     * @param loginForm
     * @param session(不需要这个session参数了)
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm) {
        //1.校验手机号格式，因为是不同请求，不正确返回错误信息
        boolean phoneInvalid = RegexUtils.isPhoneInvalid(loginForm.getPhone());
        if (phoneInvalid) {
            return Result.fail("手机号格式不正确");
        }
        //2.校验验证码，session中没有/比对错误，返回错误信息
        String rediscode = stringRedisTemplate.opsForValue().get("code:"+loginForm.getPhone());//取出redis中的验证码
        if (rediscode == null || !rediscode.equals(loginForm.getCode())) {
            return Result.fail("验证码校验失败");
        }
        //3.查询数据库看有无此用户:select * from tb_user where phone = ? ,为什么能查， ServiceImpl<UserMapper, User>该类
        //为我们实现了基本的crud，UserMapper，告诉了mapper层的接口， User，告诉了查询结果的封装类，而user表又提供了tablename，和属性对应的字段
        User user = query().eq("phone", loginForm.getPhone()).one();

        //4.用户不存在创建新用户
        if (user==null)
        {
            //准备user属性
            User newuser = new User();
            newuser.setPhone(loginForm.getPhone());
            newuser.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+ RandomUtil.randomString(10));
            newuser.setCreateTime(LocalDateTime.now());
            newuser.setUpdateTime(LocalDateTime.now());
            //保存用户
            user=newuser;
            save(user);
        }

        //5.保存用户信息到redis中，用于后期的拦截校验（不保存user的全部信息，为了隐私保护）
        //5.1 生成token，前端用这个token来登录校验，如果登陆了，就表示token对应的user在redis上，表示已登录
        String token = UUID.randomUUID().toString(true);//ture表示没有下划线的uuid，简单模式

        //5.2 生成UserDTO对象
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> usermap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));//转为map存储

        //存储userdto对象到redis中
        stringRedisTemplate.opsForHash().putAll("login:usertoken:"+token,usermap);
        //设置有效期
        //todo 改变拦截器逻辑，刷新有效期
        stringRedisTemplate.expire("login:usertoken:"+token,30,TimeUnit.MINUTES);

        // 6.返回token
        return Result.ok(token);
    }

    /**
     * 生成验证码存入session实现
     * @param phone
     * @param session
     * @return
     */
//    public Result sendcode(String phone, HttpSession session) {
//        //1.校验手机号格式，正确继续发送验证码，不正确返回错误信息
//        boolean phoneInvalid = RegexUtils.isPhoneInvalid(phone);
//        if (phoneInvalid)
//        {
//            return Result.fail("手机号格式不正确");
//        }
//
//        //获得一个只包含数字的字符串,length 字符串的长度.return 随机字符串(hutool包下的)
//        //2.生成验证码，uuid截取6位
//        String code = RandomUtil.randomNumbers(6);
//        //3.保存验证码到session，用于登录校验，加上这个phone是为了防止A账号获取验证码，用B手机号登录
//        session.setAttribute("code"+phone,code);
//        log.info("验证码是{}",code);
//        //4.返回验证码
//        return Result.ok(code);
//    }


    /**
     * session校验，实现登录功能
     * @param loginForm
     * @param session
     * @return
     */
//    @Override
//    public Result login(LoginFormDTO loginForm, HttpSession session) {
//        //1.校验手机号格式，因为是不同请求，不正确返回错误信息
//        boolean phoneInvalid = RegexUtils.isPhoneInvalid(loginForm.getPhone());
//        if (phoneInvalid) {
//            return Result.fail("手机号格式不正确");
//        }
//        //2.校验验证码，session中没有/比对错误，返回错误信息
//        Object sessioncode = session.getAttribute("code"+loginForm.getPhone());//拿到session的验证码
//        if (sessioncode == null || !sessioncode.toString().equals(loginForm.getCode())) {
//            return Result.fail("验证码校验失败");
//        }
//        //3.查询数据库看有无此用户:select * from tb_user where phone = ? ,为什么能查， ServiceImpl<UserMapper, User>该类
//        //为我们实现了基本的crud，UserMapper，告诉了mapper层的接口， User，告诉了查询结果的封装类，而user表又提供了tablename，和属性对应的字段
//        User user = query().eq("phone", loginForm.getPhone()).one();
//
//        //4.用户不存在创建新用户
//        if (user==null)
//        {
//            //准备user属性
//            User newuser = new User();
//            newuser.setPhone(loginForm.getPhone());
//            newuser.setNickName(SystemConstants.USER_NICK_NAME_PREFIX+ RandomUtil.randomString(10));
//            newuser.setCreateTime(LocalDateTime.now());
//            newuser.setUpdateTime(LocalDateTime.now());
//            //保存用户
//            user=newuser;
//            save(user);
//        }
//
//        //5.保存用户信息到session中，用于后期的拦截校验（不保存user的全部信息，为了隐私保护）
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
//        //6.返回ok
//        return Result.ok();
//    }
}
