package tech.tablesaw.io.fixed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

public class FixedWidthWriteOptionsTest {

    @Test
    public void testSettingsPropagation() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        FixedWidthWriteOptions options = new FixedWidthWriteOptions.Builder(stream)
                .header(true)
                .lineSeparatorString("\r\n")
                .padding('~')
                .build();

        assertTrue(options.header());

        FixedWidthWriter writer = new FixedWidthWriter(options);
        assertTrue(writer.getHeader());
        assertEquals("\r\n", writer.getFormat().getLineSeparatorString());
        assertEquals('~', writer.getFormat().getPadding());
    }

    @Test
    public void testSettingsAutoConfigurationPropagation() {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        FixedWidthWriteOptions options = new FixedWidthWriteOptions.Builder(stream)
                .autoConfigurationEnabled(true)
                .build();

        assertTrue(options.autoConfigurationEnabled());

        assertTrue(options.header());

        FixedWidthWriter writer = new FixedWidthWriter(options);
        assertTrue(writer.getHeader());
    }


}