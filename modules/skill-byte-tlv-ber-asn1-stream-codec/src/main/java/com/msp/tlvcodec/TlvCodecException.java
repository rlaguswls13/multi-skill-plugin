package com.msp.tlvcodec;

/**
 * Unchecked exception raised when BER-TLV encoding, decoding, or hex parsing fails.
 */
public class TlvCodecException extends RuntimeException {

    public TlvCodecException(String message) {
        super(message);
    }

    public TlvCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
