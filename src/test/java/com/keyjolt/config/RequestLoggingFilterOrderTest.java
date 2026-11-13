package com.keyjolt.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** KJ-11: requests that Spring Security denies must still reach the request log. */
@SpringBootTest
@AutoConfigureMockMvc
class RequestLoggingFilterOrderTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deniedRequestsAreLogged() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            mockMvc.perform(get("/wp-login.php")).andExpect(status().isForbidden());
            assertTrue(appender.list.stream()
                            .map(ILoggingEvent::getFormattedMessage)
                            .anyMatch(line -> line.startsWith("GET /wp-login.php 403 ")),
                    "403 logged: " + appender.list);
        } finally {
            logger.detachAppender(appender);
        }
    }
}
