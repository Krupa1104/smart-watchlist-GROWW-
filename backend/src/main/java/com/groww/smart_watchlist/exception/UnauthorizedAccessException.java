package com.groww.smart_watchlist.exception;

// Not real auth — there is none (see README). This just stops one demo user
// from reading/mutating another user's watchlist via a guessed ID, so the
// userId param isn't pure decoration. Maps to 403.
public class UnauthorizedAccessException extends RuntimeException {
    public UnauthorizedAccessException(String message) {
        super(message);
    }
}
