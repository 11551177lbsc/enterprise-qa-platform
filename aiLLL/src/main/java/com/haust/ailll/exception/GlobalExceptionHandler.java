package com.haust.ailll.exception;

import com.haust.ailll.util.ResultUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ResultUtil> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode())
                .body(ResultUtil.error(exception.getReason() == null ? "请求失败" : exception.getReason()));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ResultUtil> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(ResultUtil.error(exception.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ResultUtil> handleValidation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().isEmpty()
                ? "请求参数校验失败"
                : exception.getBindingResult().getFieldErrors().get(0).getField() + " 参数无效";
        return ResponseEntity.badRequest().body(ResultUtil.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ResultUtil> handleException(Exception exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ResultUtil.error("服务执行失败"));
    }
}
