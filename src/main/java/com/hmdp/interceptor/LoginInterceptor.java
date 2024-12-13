package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * ClassName:LoginInterceptor
 * Package:com.hmdp.interceptor
 * Description
 *
 * @Author 李明星
 * @Create 2024/10/4 14:29
 * @Version 1.0
 */
public class LoginInterceptor implements HandlerInterceptor {
    /**
     * 用于登录校验，若当前session中没有user对象，表示没有登录，要拦截
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        UserDTO user = UserHolder.getUser();
        if (user==null)
        {
            response.setStatus(401);
            return false;
        }
        return true;
//        System.out.println("LoginInterceptor拦截器");
//        //1.获取session
//        HttpSession session = request.getSession();
//        //2.获取session中的用户
//        Object user = session.getAttribute("user");
//        //3.判断用户是否存在
//        if(user == null){
//            //4.不存在，拦截，返回401状态码：(未授权) 请求要求身份验证。 对于需要登录的网页，服务器可能返回此响应。
//            response.setStatus(401);
//            return false;
//        }
//        //5.存在，保存用户信息到Threadlocal，保存在session中的本来就是userdto对象
//        //为什么要保存在localthread中，为了在返回用户直接取，那为什么不直接从session中取呢？
//        //可以在同一线程中很方便的获取用户信息，不需要频繁的传递session对象。
//        UserHolder.saveUser((UserDTO)user);
//        //6.放行
//        return true;
    }
}
