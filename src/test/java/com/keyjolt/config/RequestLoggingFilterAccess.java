package com.keyjolt.config;

/** Test bridge to the package-private redaction helper. */
public final class RequestLoggingFilterAccess {
    private RequestLoggingFilterAccess() {}

    public static String loggable(String uri) {
        return RequestLoggingFilter.loggablePath(uri);
    }
}
