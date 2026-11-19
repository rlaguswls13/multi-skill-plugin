package com.msp.cmdtokenizer;

/**
 * Unchecked exception thrown when a raw command-line string cannot be tokenized
 * (for example, due to an unterminated quote or dangling escape).
 */
public class CommandLineParseException extends RuntimeException {

    public CommandLineParseException(String message) {
        super(message);
    }
}
