package com.fooda.api.auth.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecureTokensTest {

    @Test
    void generatesDistinctUrlSafeTokensOf43Chars() {
        String a = SecureTokens.generate();
        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]+").isNotEqualTo(SecureTokens.generate());
    }

    @Test
    void hashIsSha256Hex() {
        assertThat(SecureTokens.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
