package com.cybersixseven.platformapi.service;

public class AccessoryNotReadyException extends RuntimeException {

    public AccessoryNotReadyException() {
        super("accessory is not ready");
    }
}
