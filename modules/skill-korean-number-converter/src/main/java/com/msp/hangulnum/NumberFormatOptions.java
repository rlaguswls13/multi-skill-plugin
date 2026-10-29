package com.msp.hangulnum;

/**
 * Formatting options for converting numbers into Korean words and formal financial typography.
 *
 * @param scriptType             Character script (HANGUL, HANJA_STANDARD, HANJA_FORMAL).
 * @param receiptMode            Whether to explicitly write "일/壹" before 10, 100, 1000 to prevent document alteration.
 * @param spaceEveryTenThousand  Whether to insert a space between 4-digit (만/억/조) magnitude blocks per standard Korean grammar.
 * @param prefix                 Optional prefix string (e.g., "금 ", "일금 ").
 * @param suffix                 Optional suffix string (e.g., " 원정", "원").
 */
public record NumberFormatOptions(
        ScriptType scriptType,
        boolean receiptMode,
        boolean spaceEveryTenThousand,
        String prefix,
        String suffix
) {
    public static final NumberFormatOptions DEFAULT = new NumberFormatOptions(
            ScriptType.HANGUL,
            false,
            true,
            "",
            ""
    );

    public static final NumberFormatOptions CURRENCY = new NumberFormatOptions(
            ScriptType.HANGUL,
            true,
            true,
            "금 ",
            " 원정"
    );

    public static final NumberFormatOptions HANJA_RECEIPT = new NumberFormatOptions(
            ScriptType.HANJA_FORMAL,
            true,
            true,
            "金 ",
            " 圓整"
    );

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private ScriptType scriptType = ScriptType.HANGUL;
        private boolean receiptMode = false;
        private boolean spaceEveryTenThousand = true;
        private String prefix = "";
        private String suffix = "";

        public Builder scriptType(ScriptType scriptType) {
            this.scriptType = (scriptType != null) ? scriptType : ScriptType.HANGUL;
            return this;
        }

        public Builder receiptMode(boolean receiptMode) {
            this.receiptMode = receiptMode;
            return this;
        }

        public Builder spaceEveryTenThousand(boolean spaceEveryTenThousand) {
            this.spaceEveryTenThousand = spaceEveryTenThousand;
            return this;
        }

        public Builder prefix(String prefix) {
            this.prefix = (prefix != null) ? prefix : "";
            return this;
        }

        public Builder suffix(String suffix) {
            this.suffix = (suffix != null) ? suffix : "";
            return this;
        }

        public NumberFormatOptions build() {
            return new NumberFormatOptions(
                    scriptType,
                    receiptMode,
                    spaceEveryTenThousand,
                    prefix,
                    suffix
            );
        }
    }
}
