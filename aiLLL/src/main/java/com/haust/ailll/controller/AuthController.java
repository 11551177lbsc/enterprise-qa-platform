package com.haust.ailll.controller;

import com.haust.ailll.dto.LoginDTO;
import com.haust.ailll.entity.User;
import com.haust.ailll.service.AuthService;
import com.haust.ailll.vo.LoginVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public LoginVO login(@RequestBody LoginDTO loginDTO){
        System.out.println("username = " + loginDTO.getUsername());
        System.out.println("password = " + loginDTO.getPassword());
        return authService.login(loginDTO);

    }
    // 注册
    @PostMapping("/register")
    public String register(@RequestBody User user){
        authService.register(user);
        return "注册成功";
    }
}