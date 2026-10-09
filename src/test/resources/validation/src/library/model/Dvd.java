package library.model;

/**
 * A film on a disc.
 */
public class Dvd extends Item {

    private final int minutes;

    public Dvd(String id, String title, int minutes) {
        super(id, title, Category.FILM);
        this.minutes = minutes;
    }

    public int getMinutes() {
        return minutes;
    }

    @Override
    protected double dailyRate() {
        return 0.50;
    }
}
