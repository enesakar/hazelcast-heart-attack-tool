package com.hazelcast.heartattack;

public class HeartAttackAlreadyThrownRuntimeException extends RuntimeException{

    public HeartAttackAlreadyThrownRuntimeException() {
    }

    public HeartAttackAlreadyThrownRuntimeException(Throwable cause) {
        super(cause);
    }

    public HeartAttackAlreadyThrownRuntimeException(String message) {
        super(message);
    }

    public HeartAttackAlreadyThrownRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public HeartAttackAlreadyThrownRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
