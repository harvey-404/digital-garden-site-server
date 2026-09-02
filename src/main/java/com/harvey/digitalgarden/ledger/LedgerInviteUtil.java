package com.harvey.digitalgarden.ledger;

import com.harvey.digitalgarden.repository.LedgerUserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class LedgerInviteUtil {

    static final String ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final SecureRandom RANDOM = new SecureRandom();

    private LedgerInviteUtil() {}

    public static String normalizeInviteCode(String raw) {
        String stripped = raw.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        if (stripped.length() != 12) {
            throw new IllegalArgumentException(
                    "Invite code must be 12 alphanumeric characters after normalization");
        }
        return formatInviteCode(stripped);
    }

    public static String newInviteCode() {
        return formatInviteCode(randomAlphabet(12));
    }

    public static String allocateUserSn(LedgerUserRepository repo) {
        String datePart = LocalDate.now(SHANGHAI).format(DateTimeFormatter.BASIC_ISO_DATE);
        String dayPrefix = "L" + datePart + "-";
        int seq = (int) repo.countByUserSnStartingWith(dayPrefix) + 1;
        if (seq > 9999) {
            throw new IllegalStateException("Daily user_sn sequence exhausted for " + datePart);
        }
        for (int attempt = 0; attempt < 100; attempt++) {
            String candidate = dayPrefix + String.format("%04d", seq) + "-" + randomAlphabet(8);
            if (repo.findByUserSn(candidate).isEmpty()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Failed to allocate unique user_sn");
    }

    public static String allocateInviteCode(LedgerUserRepository repo) {
        for (int attempt = 0; attempt < 100; attempt++) {
            String code = newInviteCode();
            if (repo.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("Failed to allocate unique invite code");
    }

    private static String formatInviteCode(String twelveChars) {
        return twelveChars.substring(0, 4)
                + "-"
                + twelveChars.substring(4, 8)
                + "-"
                + twelveChars.substring(8, 12);
    }

    private static String randomAlphabet(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
