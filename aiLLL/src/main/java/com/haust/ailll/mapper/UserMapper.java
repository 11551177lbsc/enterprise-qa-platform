package com.haust.ailll.mapper;

import com.haust.ailll.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {

    User findByUsername(String username);
    User findById(Long id);   // ⭐加这一行
    void insert(User user);

}