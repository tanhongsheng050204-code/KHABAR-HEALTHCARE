package com.khabar.api.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiErrorsTest {

    @Test
    void unexpectedErrorsReturnTheSupportReferenceWithoutExposingExceptionText() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE, "server-generated-reference");

        ApiErrors.ApiError body = new ApiErrors()
                .handleUnexpected(new IllegalStateException("patient name and health details"), request)
                .getBody();

        assertThat(body).isNotNull();
        assertThat(body.code()).isEqualTo("server_error");
        assertThat(body.status()).isEqualTo(500);
        assertThat(body.requestId()).isEqualTo("server-generated-reference");
        assertThat(body.retryable()).isFalse();
        assertThat(body.message()).doesNotContain("patient name", "health details");
    }

    @Test
    void requestIdsAreGeneratedByTheServerAndReturnedInTheResponseHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestCorrelationFilter.REQUEST_ID_HEADER, "caller-controlled-value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new RequestCorrelationFilter().doFilter(request, response, new MockFilterChain());

        String requestId = response.getHeader(RequestCorrelationFilter.REQUEST_ID_HEADER);
        assertThat(requestId).isNotBlank().isNotEqualTo("caller-controlled-value");
        assertThat(request.getAttribute(RequestCorrelationFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo(requestId);
        assertThat(requestId).matches("[0-9a-f-]{36}");
    }

    @Test
    void onlyKnownRateLimitErrorsAreMarkedRetryable() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        ApiErrors errors = new ApiErrors();

        assertThat(errors.handle(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS), request).getBody().retryable()).isTrue();
        assertThat(errors.handle(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE), request).getBody().retryable()).isFalse();
    }
}
