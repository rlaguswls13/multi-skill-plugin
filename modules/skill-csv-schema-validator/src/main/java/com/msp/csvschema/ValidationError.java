package com.msp.csvschema;

import java.util.Collections;
import java.util.List;

public record ValidationError(long rowNumber, String columnName, String rawValue, String message) {}
