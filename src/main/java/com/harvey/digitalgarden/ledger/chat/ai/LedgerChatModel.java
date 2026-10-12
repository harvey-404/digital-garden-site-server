package com.harvey.digitalgarden.ledger.chat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.harvey.digitalgarden.ledger.chat.ChatDtos.*;
import java.util.List;

public interface LedgerChatModel {
    record Input(String text,long receivedAt,List<StoredImage> images,List<Long> attachmentIds,
                 List<?> categories,Draft draft,List<Message> history) {}
    JsonNode parse(Input input);
    boolean available();
}
