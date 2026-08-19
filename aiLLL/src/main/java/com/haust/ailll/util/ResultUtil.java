package com.haust.ailll.util;

import lombok.Data;

@Data
public class ResultUtil {

    private Integer code;

    private String message;

    private Object data;

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public static ResultUtil success(Object data){

        ResultUtil result = new ResultUtil();

        result.setCode(200);
        result.setMessage("success");
        result.setData(data);

        return result;

    }

    public static ResultUtil error(String msg){

        ResultUtil result = new ResultUtil();

        result.setCode(500);
        result.setMessage(msg);

        return result;

    }

}