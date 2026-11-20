package com.gsgd.generic_erp.configuration.exception;

import org.springframework.dao.DataIntegrityViolationException;

/**
 * A uniqueness check the code made itself (for example "username already in
 * use"). Unlike a raw database constraint error, its message is already a
 * localized, user-safe sentence, so the error handler sends it as-is.
 */
public class DuplicateValueException extends DataIntegrityViolationException {

    private static final long serialVersionUID = 1L;

    public DuplicateValueException(String localizedMessage) {
        super(localizedMessage);
    }
}
