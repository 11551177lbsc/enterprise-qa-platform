package com.haust.ailll.controller;

import com.haust.ailll.entity.User;
import com.haust.ailll.dto.agenttool.UserProfileResponse;
import com.haust.ailll.service.UserService;
import com.haust.ailll.util.ResultUtil;

import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/{id}")
    public ResultUtil getUser(@PathVariable Long id, HttpServletRequest request){

        Long authenticatedId = (Long) request.getAttribute("authenticatedUserId");
        if (authenticatedId == null || !authenticatedId.equals(id)) {
            throw new IllegalArgumentException("只能查询当前登录用户");
        }

        User user = userService.findById(id);

        return ResultUtil.success(new UserProfileResponse(user.getId(), user.getUsername(), user.getEmail()));

    }

}
