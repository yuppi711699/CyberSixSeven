package com.cybersixseven.platformapi.service;

public class ProductUnavailableException extends RuntimeException {

    public ProductUnavailableException() {
        super("product routes are disabled until v0.8");
    }
}
