package com.haust.ailll.service.impl;

import com.haust.ailll.entity.User;
import com.haust.ailll.mapper.UserMapper;
import com.haust.ailll.service.UserService;

import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public User findById(Long id) {

        return userMapper.findById(id);

    }

    @Override
    public void register(User user) {

        userMapper.insert(user);

    }

}
