package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;

public class LoginInterceptor implements HandlerInterceptor {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 使用redis, 配置两个拦截器
        // 1. 得到ThreadLocal中的用户
        UserDTO userDTO = UserHolder.getUser();
        // 2. 如果ThreadLocal中的用户是null, 拦截
        if (userDTO == null) {
            response.setStatus(401);
            return false;
        }
        // 3. 放行
        return true;

        //使用redis时, 且仅仅配置这一个拦截器
//        // 1. 从redis中获取用户
//        String token = request.getHeader("authorization");
//        if (StrUtil.isBlank(token)) {
//            response.setStatus(401);
//            return false;
//        }
//        String tokenKey = LOGIN_USER_KEY + token;
//        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);
//        // 2. 如果用户不存在, 拦截
//        if (userMap.isEmpty()) {
//            response.setStatus(401);
//            return false;
//        }
//        // 3. 将userDTO保存到ThreadLocal中
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
//        UserHolder.saveUser(userDTO);
//        // 4. 刷新token有效期
//        stringRedisTemplate.expire(token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
//        // 5. 放行
//        return true;

//        使用session时, 并且仅仅配置这一个拦截器
//        // 1. 获取session
//        HttpSession session = request.getSession();
//        // 2. 获取session中的用户
//        Object user = session.getAttribute("user");
//        // 4. 如果用户不存在, 拦截
//        if (user == null) {
//            response.setStatus(401);
//            return false;
//        }
//        // 5. 保存用户到TheadLocal中
//        UserHolder.saveUser((UserDTO) user);
//        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
