package top.enderherman.wetalk.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import top.enderherman.wetalk.common.BaseResponse;
import top.enderherman.wetalk.common.ResponseCodeEnum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ErrorRedactionTest {

    @Test
    void returnsGenericErrorAndCorrelationIdWithoutExceptionDetails() {
        AGlobalExceptionHandlerController handler = new AGlobalExceptionHandlerController();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/account/login");
        MockHttpServletResponse servletResponse = new MockHttpServletResponse();

        BaseResponse<?> response = (BaseResponse<?>) handler.handleException(
                new IllegalStateException("private database value"), request, servletResponse);

        assertEquals(ResponseCodeEnum.CODE_500.getCode(), response.getCode());
        assertFalse(response.getMessage().contains("private database value"));
        assertNotNull(servletResponse.getHeader("X-Request-Id"));
        assertTrue(servletResponse.getHeader("X-Request-Id").matches("[0-9a-f-]{36}"));
    }
}
