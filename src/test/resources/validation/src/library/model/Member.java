package library.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A registered member of the library.
 */
public class Member {

    private final String name;
    private final List<Item> loans = new ArrayList<>();

    public Member(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    void addLoan(Item item) {
        loans.add(item);
    }

    void removeLoan(Item item) {
        loans.remove(item);
    }

    public int loanCount() {
        return loans.size();
    }

    public List<Item> getLoans() {
        return List.copyOf(loans);
    }
}
