package com.msp.bencode;

/**
 * Sealed interface representing all valid Bencode values according to BEP 0003:
 * Integers, Byte Strings, Lists, and Dictionaries.
 */
public sealed interface BencodeValue permits BencodeInteger, BencodeString, BencodeList, BencodeDict {
}
