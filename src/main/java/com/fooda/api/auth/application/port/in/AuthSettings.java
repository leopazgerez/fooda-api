package com.fooda.api.auth.application.port.in;

import java.time.Duration;

public record AuthSettings(Duration refreshTtl, String frontendUrl) {
}
