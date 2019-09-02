package com.jonflacke.hedgehog.exceptions;

import org.springframework.http.HttpStatus;

/**
 * Created by Jon on 9/8/2018.
 */
public class BadRequestException extends RuntimeException {
    private static final HttpStatus status = HttpStatus.BAD_REQUEST;
    private String message;

    public BadRequestException(String message) {
        this.message = message;
    }

    public static HttpStatus getStatus() {
        return status;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
