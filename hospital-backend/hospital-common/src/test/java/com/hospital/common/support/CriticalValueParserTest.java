package com.hospital.common.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CriticalValueParserTest {

    @Test
    void parse_detectsHighWbc() {
        String text = "白细胞 WBC        12.8 ×10⁹/L    参考 3.5-9.5  ↑";
        List<CriticalValueParser.CriticalItem> items = CriticalValueParser.parse(text);
        assertEquals(1, items.size());
        assertEquals("白细胞 WBC", items.get(0).name());
        assertEquals("12.8", items.get(0).value());
        assertEquals("HIGH", items.get(0).flag());
    }

    @Test
    void parse_ignoresNormalLine() {
        String text = "血红蛋白 Hb       138 g/L       参考 130-175";
        assertTrue(CriticalValueParser.parse(text).isEmpty());
    }

    @Test
    void parse_detectsLowValue() {
        String text = "血红蛋白 Hb       110 g/L       参考 130-175  ↓";
        List<CriticalValueParser.CriticalItem> items = CriticalValueParser.parse(text);
        assertEquals(1, items.size());
        assertEquals("LOW", items.get(0).flag());
    }

    @Test
    void parse_skipsUnparseableLines() {
        String text = "采样时间：仪器自动记录\n【仪器原始数据 · 只读】";
        assertTrue(CriticalValueParser.parse(text).isEmpty());
    }
}
