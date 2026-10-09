package library.model;

/**
 * Something that a member can borrow.
 */
public interface Loanable {

    boolean isAvailable();

    void checkOut(Member member);

    void giveBack();

    double fee(int days);
}
