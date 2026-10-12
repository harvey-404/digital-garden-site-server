package com.harvey.digitalgarden.ledger.chat;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

public final class ChatDtos {
    private ChatDtos() {}
    public record Issue(String code, String message) {}
    @Data public static class Item {
        private long id;
        private int ordinal;
        private String amount;
        private Long categoryId;
        private String description = "";
        private Long recordedAt;
        private String datePrecision = "DATE";
        private boolean defaultedToday;
        private boolean future;
        private long sourceMessageId;
        private List<Long> sourceAttachmentIds = new ArrayList<>();
        private String evidence = "";
        private List<Issue> issues = new ArrayList<>();
        private List<Long> duplicateExpenseIds = new ArrayList<>();
        private List<SavedExpense> duplicateRecords = new ArrayList<>();
        private boolean duplicateAcknowledged;
        private String orderGroupKey;
        private String orderPaidTotal;
    }
    public record SavedExpense(long id, String amount, long categoryId, String description, long recordedAt) {}
    public record Draft(long id, long version, String status, List<Item> items, String total,
                        int count, boolean canConfirm, List<SavedExpense> savedExpenses) {}
    public record Message(long id, String role, String text, List<Long> attachmentIds,
                          long createdAt, String requestId, String status, Long draftId) {}
    public record Capabilities(boolean available, boolean imageInput) {}
    public record History(List<Message> messages, List<Draft> drafts, Draft activeDraft,
                          Long nextBeforeId, String processingRequestId, Capabilities capabilities, LedgerGuestService.View guest) {}
    public record Send(String requestId, String text, List<Long> attachmentIds, Long expectedDraftVersion) {}
    public record Turn(Message userMessage, Message assistantMessage, Draft draft) {}
    public record Edit(long expectedVersion, String amount, Long categoryId, String description,
                       Long recordedAt, String datePrecision, boolean duplicateAcknowledged) {}
    public record Version(long expectedVersion) {}
    public record Attachment(long id, String contentType, int width, int height) {}
    public record StoredImage(byte[] bytes, String contentType, int width, int height, String hash) {}
    public record RequestState(String status, Turn turn) {}
}
