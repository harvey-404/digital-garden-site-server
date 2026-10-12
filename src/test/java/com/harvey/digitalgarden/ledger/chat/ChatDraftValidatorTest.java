package com.harvey.digitalgarden.ledger.chat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;

class ChatDraftValidatorTest {
    private Object issue(String amount) throws Exception {
        Class<?> type = assertDoesNotThrow(() -> Class.forName("com.harvey.digitalgarden.ledger.chat.ChatDraftValidator"));
        Method method = type.getMethod("amountIssue", String.class);
        return method.invoke(null, amount);
    }
    @Test void missingAmountMustBeAskedFor() throws Exception { assertNotNull(issue(null)); }
    @Test void extraPrecisionIsRejected() throws Exception { assertNotNull(issue("1.001")); }
    @Test void nonPositiveIsRejected() throws Exception { assertNotNull(issue("0")); }
    @Test void aboveLimitIsRejected() throws Exception { assertNotNull(issue("1000000")); }
    @Test void validAmountIsAccepted() throws Exception { assertNull(issue("32.50")); }
}
