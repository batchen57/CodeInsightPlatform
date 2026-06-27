package com.company.codeinsight.common.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

public class Base62GeneratorTest {

    private final Base62Generator generator = new Base62Generator();

    @Test
    public void testGenerateLengthIsFive() {
        for (int i = 0; i < 100; i++) {
            Assertions.assertEquals(5, generator.generate().length());
        }
    }

    @Test
    public void testCharsetIsBase62() {
        String allowed = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        for (int i = 0; i < 200; i++) {
            String id = generator.generate();
            for (char c : id.toCharArray()) {
                Assertions.assertTrue(allowed.indexOf(c) >= 0, "unexpected char: " + c);
            }
        }
    }

    @Test
    public void testGenerateWithPrefix() {
        for (int i = 0; i < 100; i++) {
            Assertions.assertTrue(generator.generateWithPrefix('m').startsWith("m"));
            Assertions.assertEquals(6, generator.generateWithPrefix('s').length());
        }
    }

    @Test
    public void testGenerateUniqueRetryOnCollision() {
        Set<String> existing = new HashSet<>();
        existing.add("m00000");
        String id = generator.generateUnique('m', existing);
        Assertions.assertNotEquals("m00000", id);
        Assertions.assertTrue(id.startsWith("m"));
    }
}