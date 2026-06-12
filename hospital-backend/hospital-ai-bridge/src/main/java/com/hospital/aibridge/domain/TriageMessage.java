package com.hospital.aibridge.domain;

import java.time.Instant;

public record TriageMessage(
        String role,
        String content,
        Instant time
) {
}
