package com.keyjolt.util;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * KJ-08: the download token is the only thing protecting a key until it is
 * deleted, and PGP file names contain the e-mail address. Neither may be logged.
 */
class LogPrivacyTest {

    @TempDir
    Path tempDir;

    @Test
    void writingAndDeletingKeyFilesLogsNeitherTokenNorFileName() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(FileUtils.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        FileUtils fileUtils = new FileUtils();
        ReflectionTestUtils.setField(fileUtils, "tempDir", tempDir.resolve("keyjolt").toString());
        ReflectionTestUtils.setField(fileUtils, "cleanupDelay", 50L);
        try {
            fileUtils.initTempDirectory();
            String token = fileUtils.newRequestDir();
            fileUtils.writeToTempFile(token, "jane@example.com_Jane_seckey_ABCDEF.asc",
                    "secret".getBytes(StandardCharsets.UTF_8), true);
            Thread.sleep(400); // let the timed deletion run and log

            for (ILoggingEvent event : appender.list) {
                String line = event.getFormattedMessage();
                assertFalse(line.contains(token), line);
                assertFalse(line.contains("jane@example.com"), line);
                assertFalse(line.contains("seckey"), line);
            }
            assertFalse(appender.list.isEmpty(), "something was logged");
        } finally {
            fileUtils.shutdown();
            logger.detachAppender(appender);
        }
    }

    @Test
    void downloadPathsAreRedactedInTheRequestLog() {
        assertEquals("/download/<redacted>", com.keyjolt.config.RequestLoggingFilterAccess.loggable(
                "/download/0123456789abcdef0123456789abcdef/jane@example.com_Jane_seckey_AB.asc"));
        assertEquals("/api/generate", com.keyjolt.config.RequestLoggingFilterAccess.loggable("/api/generate"));
        assertEquals("/", com.keyjolt.config.RequestLoggingFilterAccess.loggable("/"));
    }
}
