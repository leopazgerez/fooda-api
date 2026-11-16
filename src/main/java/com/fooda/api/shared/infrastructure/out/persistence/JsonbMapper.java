package com.fooda.api.shared.infrastructure.out.persistence;

import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/** Fixed format for JSONB columns, independent of spring.jackson.* (spec §5.7). */
public final class JsonbMapper {

    public static final JsonMapper JSON = JsonMapper.builder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private JsonbMapper() {
    }
}
