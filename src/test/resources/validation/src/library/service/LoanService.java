package library.service;

import java.util.ArrayList;
import java.util.List;

import library.model.Item;
import library.model.Loanable;
import library.model.Member;
import library.util.TextUtils;

/**
 * Registers loans and returns.
 */
public class LoanService {

    public static final int MAX_LOANS = 3;

    private final Catalog catalog;
    private final Notifier notifier;
    private final List<Loan> history = new ArrayList<>();
    int refusals;

    public LoanService(Catalog catalog, Notifier notifier) {
        this.catalog = catalog;
        this.notifier = notifier;
    }

    public Loan borrow(Member member, String itemId, int days) throws LoanException {
        if (member.loanCount() >= MAX_LOANS) {
            refusals++;
            throw new LoanException(member.getName() + " has too many loans");
        }
        Item item = catalog.findById(itemId)
                .orElseThrow(() -> new LoanException("Unknown item " + itemId));
        Loanable loanable = item;
        loanable.checkOut(member);
        Loan loan = new Loan(item, member, days);
        history.add(loan);
        notifier.send(member, "borrowed " + TextUtils.capitalize(item.getTitle()));
        return loan;
    }

    public void giveBack(Loan loan) {
        loan.getItem().giveBack();
        notifier.send(loan.getMember(), "returned " + loan.getItem().getTitle());
    }

    public double totalFees() {
        double total = 0;
        for (Loan loan : history) {
            total += loan.getItem().fee(loan.getDays());
        }
        return total;
    }

    public List<Loan> getHistory() {
        return history;
    }

    public int getRefusals() {
        return refusals;
    }

    /**
     * One loan of one item by one member.
     */
    public static class Loan {

        private final Item item;
        private final Member member;
        private final int days;

        Loan(Item item, Member member, int days) {
            this.item = item;
            this.member = member;
            this.days = days;
        }

        public Item getItem() {
            return item;
        }

        public Member getMember() {
            return member;
        }

        public int getDays() {
            return days;
        }
    }
}
