package com.viaversion.mappingsgenerator;

import java.io.IOException;
import org.junit.jupiter.api.Test;

public class MappingsTest {

    @Test
    void testFilledStatus() throws IOException {
        ManualRunner.runAll(ErrorStrategy.ERROR);
    }
}
