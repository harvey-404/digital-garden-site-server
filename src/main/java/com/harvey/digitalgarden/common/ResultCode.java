package com.harvey.digitalgarden.common;

public final class ResultCode {
    private ResultCode() {}
    public static final int SUCCESS = 0;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int ERROR = 500;
}
