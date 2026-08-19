package com.haust.ailll.service.impl;

import com.haust.ailll.dto.LoginDTO;
import com.haust.ailll.entity.User;
import com.haust.ailll.mapper.UserMapper;
import com.haust.ailll.service.AuthService;
import com.haust.ailll.util.JwtUtil;
import com.haust.ailll.util.RedisUtil;
import com.haust.ailll.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.Duration;

@Service
public class AuthServiceImpl implements AuthService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;
    @Autowired
    private RedisUtil redisUtil;
    @Override
    public LoginVO login(LoginDTO loginDTO) {

        User user = userMapper.findByUsername(loginDTO.getUsername());

        if(user == null){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"用户不存在");
        }

        if(!BCrypt.checkpw(loginDTO.getPassword(),user.getPassword())){
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"密码错误");
        }

        String token = jwtUtil.generateToken(user.getId());

        // Redis保存token
        redisUtil.set("login:" + user.getId(), token, Duration.ofDays(1).toSeconds());

        LoginVO vo = new LoginVO();
        vo.setToken(token);
        vo.setUserId(user.getId());

        return vo;
    }

    @Override
    public void register(User user) {
        // 判断用户是否存在
        User existUser = userMapper.findByUsername(user.getUsername());

        if(existUser != null){
            throw new RuntimeException("用户名已存在");
        }

        // 密码加密
        String encodePwd = BCrypt.hashpw(user.getPassword(), BCrypt.gensalt());

        user.setPassword(encodePwd);

        userMapper.insert(user);
    }

}
