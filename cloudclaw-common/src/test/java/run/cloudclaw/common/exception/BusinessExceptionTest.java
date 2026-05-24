package run.cloudclaw.common.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BusinessException")
class BusinessExceptionTest {

    @Test
    @DisplayName("message 构造应使用默认 code 400")
    void messageOnly_defaultCode400() {
        BusinessException ex = new BusinessException("Something went wrong");
        assertEquals(400, ex.getCode());
        assertEquals("Something went wrong", ex.getMessage());
    }

    @Test
    @DisplayName("code + message 构造应正确设置")
    void codeAndMessage() {
        BusinessException ex = new BusinessException(404, "Not found");
        assertEquals(404, ex.getCode());
        assertEquals("Not found", ex.getMessage());
    }

    @Test
    @DisplayName("带 cause 的构造应保留原始异常")
    void withCause() {
        RuntimeException cause = new RuntimeException("root cause");
        BusinessException ex = new BusinessException(500, "internal error", cause);
        assertEquals(500, ex.getCode());
        assertEquals("internal error", ex.getMessage());
        assertSame(cause, ex.getCause());
    }

    @Test
    @DisplayName("应是 RuntimeException 的子类")
    void isRuntimeException() {
        assertTrue(RuntimeException.class.isAssignableFrom(BusinessException.class));
    }
}
