package library.model;

/**
 * Broad category of an item of the catalogue.
 */
public enum Category {
    NOVEL("Novel"),
    SCIENCE("Science"),
    FILM("Film");

    private final String label;

    Category(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
