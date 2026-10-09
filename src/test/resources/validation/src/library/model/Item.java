package library.model;

/**
 * Common state and behaviour of every item of the catalogue.
 */
public abstract class Item implements Loanable {

    public static final int MAX_LOAN_DAYS = 21;

    protected final String id;
    protected String title;
    private Member borrower;
    Category category;

    protected Item(String id, String title, Category category) {
        this.id = id;
        this.title = title;
        this.category = category;
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public Category getCategory() {
        return category;
    }

    @Override
    public boolean isAvailable() {
        return borrower == null;
    }

    @Override
    public void checkOut(Member member) {
        if (!isAvailable()) {
            throw new IllegalStateException(title + " is already borrowed");
        }
        borrower = member;
        member.addLoan(this);
    }

    @Override
    public void giveBack() {
        if (borrower != null) {
            borrower.removeLoan(this);
            borrower = null;
        }
    }

    public String describe() {
        return id + " - " + title + " (" + category.getLabel() + ")";
    }

    protected abstract double dailyRate();

    @Override
    public double fee(int days) {
        int billed = Math.min(days, MAX_LOAN_DAYS);
        return billed * dailyRate();
    }
}
