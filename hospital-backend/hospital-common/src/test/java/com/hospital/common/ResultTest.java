package com.hospital.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void success_withData() {
        Result<String> result = Result.success("ok");

        assertEquals(200, result.getCode());
        assertEquals("操作成功", result.getMessage());
        assertEquals("ok", result.getData());
        assertTrue(result.getSuccess());
    }

    @Test
    void success_withCustomMessage() {
        Result<Integer> result = Result.success("已保存", 42);

        assertEquals("已保存", result.getMessage());
        assertEquals(42, result.getData());
        assertTrue(result.getSuccess());
    }

    @Test
    void success_noData() {
        Result<Void> result = Result.success();

        assertNull(result.getData());
        assertTrue(result.getSuccess());
    }

    @Test
    void fail_withCode() {
        Result<Void> result = Result.fail(400, "参数错误");

        assertEquals(400, result.getCode());
        assertEquals("参数错误", result.getMessage());
        assertNull(result.getData());
        assertFalse(result.getSuccess());
    }

    @Test
    void fail_defaultCode() {
        Result<Void> result = Result.fail("服务器错误");

        assertEquals(500, result.getCode());
        assertFalse(result.getSuccess());
    }
}
