package com.haust.ailll.controller;

import com.haust.ailll.entity.User;
import com.haust.ailll.service.UserService;
import com.haust.ailll.util.ResultUtil;

import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/{id}")
    public ResultUtil getUser(@PathVariable Long id){

        User user = userService.findById(id);

        return ResultUtil.success(user);

    }

}