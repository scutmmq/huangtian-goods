package com.scutmmq.ai.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AiMemoryPropertiesTest {

    @Test
    void rejectsSecretShorterThan32Chars() {
        AiMemoryProperties p = new AiMemoryProperties();
        p.setCacheHmacSecrets("v1:short");
        p.setActiveSecretVersion("v1");
        assertThrows(IllegalStateException.class, p::validate);
    }

    @Test
    void rejectsActiveVersionNotInSecrets() {
        AiMemoryProperties p = new AiMemoryProperties();
        p.setCacheHmacSecrets("v1:abcdefghijklmnopqrstuvwxyz123456");
        p.setActiveSecretVersion("v2");
        assertThrows(IllegalStateException.class, p::validate);
    }

    @Test
    void acceptsValidConfig() {
        AiMemoryProperties p = new AiMemoryProperties();
        p.setCacheHmacSecrets("v1:abcdefghijklmnopqrstuvwxyz123456");
        p.setActiveSecretVersion("v1");
        assertDoesNotThrow(p::validate);
    }
}