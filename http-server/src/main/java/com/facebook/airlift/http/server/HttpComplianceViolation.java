package com.facebook.airlift.http.server;

import org.eclipse.jetty.http.HttpCompliance.Violation;

import static java.util.Objects.requireNonNull;

public enum HttpComplianceViolation
{
    CASE_SENSITIVE_FIELD_NAME(Violation.CASE_SENSITIVE_FIELD_NAME),
    CASE_INSENSITIVE_METHOD(Violation.CASE_INSENSITIVE_METHOD),
    HTTP_0_9(Violation.HTTP_0_9),
    MULTILINE_FIELD_VALUE(Violation.MULTILINE_FIELD_VALUE),
    MULTIPLE_CONTENT_LENGTHS(Violation.MULTIPLE_CONTENT_LENGTHS),
    TRANSFER_ENCODING_WITH_CONTENT_LENGTH(Violation.TRANSFER_ENCODING_WITH_CONTENT_LENGTH),
    WHITESPACE_AFTER_FIELD_NAME(Violation.WHITESPACE_AFTER_FIELD_NAME),
    NO_COLON_AFTER_FIELD_NAME(Violation.NO_COLON_AFTER_FIELD_NAME),
    DUPLICATE_HOST_HEADERS(Violation.DUPLICATE_HOST_HEADERS),
    UNSAFE_HOST_HEADER(Violation.UNSAFE_HOST_HEADER),
    MISMATCHED_AUTHORITY(Violation.MISMATCHED_AUTHORITY);

    private final Violation httpComplianceViolation;

    HttpComplianceViolation(Violation httpComplianceViolation)
    {
        this.httpComplianceViolation = requireNonNull(httpComplianceViolation, "httpComplianceViolation is null");
    }

    public Violation getHttpComplianceViolation()

    {
        return httpComplianceViolation;
    }
}
