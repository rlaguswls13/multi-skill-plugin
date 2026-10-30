package com.msp.csvschema;

import java.util.*;

public final class CsvSchema {

    private final List<ColumnRule> columns;

    public CsvSchema(List<ColumnRule> columns) {
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<ColumnRule> columns() {
        return columns;
    }

    public static final class Builder {
        private final List<ColumnRule> rules = new ArrayList<>();

        public Builder column(ColumnRule rule) {
            rules.add(Objects.requireNonNull(rule, "rule must not be null"));
            return this;
        }

        public Builder column(String name, DataType type, boolean required) {
            rules.add(ColumnRule.builder(name, type).required(required).build());
            return this;
        }

        public CsvSchema build() {
            return new CsvSchema(rules);
        }
    }
}
