package net.vaemendis.hccd;

public enum CopiesValue {
    NONE(null, "[disabled]"),
    COPIES("_copies", "_copies");

    public final String value;
    public final String label;

    CopiesValue(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public boolean isEnabled() {
        return this.value != null;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
