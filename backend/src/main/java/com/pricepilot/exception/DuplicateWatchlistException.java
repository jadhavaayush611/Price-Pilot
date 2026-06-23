package com.pricepilot.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateWatchlistException extends RuntimeException {
    public DuplicateWatchlistException(String message) {
        super(message);
    }
}
