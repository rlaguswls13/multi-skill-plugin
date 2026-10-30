package com.msp.csvschema;

import java.util.Collections;
import java.util.List;

public record ValidationReport(long totalRows, List<ValidationError> errors) {

    public ValidationReport {
        errors = (errors != null) ? Collections.unmodifiableList(errors) : Collections.emptyList();
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public int errorCount() {
        return errors.size();
    }
}
