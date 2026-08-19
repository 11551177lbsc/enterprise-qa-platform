package com.haust.ailll.service;

import com.haust.ailll.entity.User;

public interface UserService {

    User findById(Long id);

    void register(User user);

}
