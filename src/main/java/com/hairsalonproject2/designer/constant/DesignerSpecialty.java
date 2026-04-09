package com.hairsalonproject2.designer.constant;

public enum DesignerSpecialty {
    CUT("커트"),
    PERM("펌"),
    COLOR("염색"),
    STYLING("스타일링");

    private final String label;

    DesignerSpecialty(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
