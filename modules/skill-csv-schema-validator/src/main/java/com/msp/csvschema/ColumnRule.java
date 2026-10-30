package com.msp.csvschema;

import java.util.Objects;
import java.util.regex.Pattern;

public final class ColumnRule {

    private final String name;
    private final DataType dataType;
    private final boolean required;
    private final Pattern regex;
    private final Double min;
    private final Double max;

    private ColumnRule(Builder builder) {
        this.name = Objects.requireNonNull(builder.name, "name must not be null");
        this.dataType = Objects.requireNonNull(builder.dataType, "dataType must not be null");
        this.required = builder.required;
        this.regex = builder.regex;
        this.min = builder.min;
        this.max = builder.max;
    }

    public static Builder builder(String name, DataType dataType) {
        return new Builder(name, dataType);
    }

    public String name() { return name; }
    public DataType dataType() { return dataType; }
    public boolean required() { return required; }
    public Pattern regex() { return regex; }
    public Double min() { return min; }
    public Double max() { return max; }

    public static final class Builder {
        private final String name;
        private final DataType dataType;
        private boolean required = false;
        private Pattern regex = null;
        private Double min = null;
        private Double max = null;

        private Builder(String name, DataType dataType) {
            this.name = name;
            this.dataType = dataType;
        }

        public Builder required(boolean required) {
            this.required = required;
            return this;
        }

        public Builder regex(String regexPattern) {
            this.regex = Pattern.compile(regexPattern);
            return this;
        }

        public Builder range(double min, double max) {
            this.min = min;
            this.max = max;
            return this;
        }

        public ColumnRule build() {
            return new ColumnRule(this);
        }
    }
}
