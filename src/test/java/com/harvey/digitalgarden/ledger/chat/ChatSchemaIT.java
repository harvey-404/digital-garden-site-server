package com.harvey.digitalgarden.ledger.chat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@ActiveProfiles("chat-test")
class ChatSchemaIT {
    @Autowired JdbcTemplate jdbc;
    @Test void createsChatTablesAndExpenseVersion() {
        assertEquals("ledger_chat_test", jdbc.queryForObject("SELECT DATABASE()", String.class));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name LIKE 'ledger_chat_%'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='ledger_expense' AND column_name='record_version'", Integer.class));
    }
}
