package com.haust.ailll;

import org.junit.jupiter.api.Test;
import com.haust.ailll.util.JwtUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AilllApplicationTests {

    @Test
    void jwtRoundTripUsesConfiguredSecret() {
        JwtUtil jwtUtil = new JwtUtil("0123456789abcdef0123456789abcdef");
        String token = jwtUtil.generateToken(17L);
        assertEquals(17L, jwtUtil.parseToken(token));
    }

    @Test
    void jwtRejectsShortSecret() {
        assertThrows(IllegalStateException.class, () -> new JwtUtil("too-short"));
    }

}
