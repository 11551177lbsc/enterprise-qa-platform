package com.haust.ailll.service;

import com.haust.ailll.dto.LoginDTO;
import com.haust.ailll.entity.User;
import com.haust.ailll.vo.LoginVO;

public interface AuthService {

    LoginVO login(LoginDTO loginDTO);

    void register(User user);

}
