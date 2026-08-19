package com.haust.ailll.exception;

import com.haust.ailll.util.ResultUtil;

import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResultUtil handleException(Exception e){

        return ResultUtil.error(e.getMessage());

    }

}