package library.model;

/**
 * A printed book.
 */
public class Book extends Item {

    private final String author;
    private int pages;

    public Book(String id, String title, String author, int pages) {
        super(id, title, Category.NOVEL);
        this.author = author;
        this.pages = pages;
    }

    public String getAuthor() {
        return author;
    }

    public int getPages() {
        return pages;
    }

    @Override
    protected double dailyRate() {
        return pages > 500 ? 0.20 : 0.10;
    }

    @Override
    public String describe() {
        return super.describe() + " by " + author;
    }
}
