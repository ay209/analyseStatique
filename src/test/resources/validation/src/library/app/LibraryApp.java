package library.app;

import java.util.List;

import library.model.Book;
import library.model.Dvd;
import library.model.Item;
import library.model.Member;
import library.service.Catalog;
import library.service.ConsoleNotifier;
import library.service.LoanException;
import library.service.LoanService;
import library.util.Statistics;

/**
 * Entry point of the validation project.
 */
public class LibraryApp {

    public static void main(String[] args) {
        Catalog catalog = new Catalog();
        Book dune = new Book("B1", "dune", "Frank Herbert", 612);
        Book zadig = new Book("B2", "zadig", "Voltaire", 150);
        catalog.add(dune, zadig);
        catalog.add(new Dvd("D1", "metropolis", 153));

        ConsoleNotifier notifier = new ConsoleNotifier();
        LoanService service = new LoanService(catalog, notifier);
        Member alice = new Member("Alice");
        run(service, alice, List.of("B1", "D1", "B2", "B9"));

        System.out.println(dune.describe() + " / " + zadig.getTitle());
        System.out.println(Statistics.report("Library", catalog.size(), 1, service.totalFees(), service.getRefusals()));
    }

    static void run(LoanService service, Member member, List<String> ids) {
        for (String id : ids) {
            try {
                LoanService.Loan loan = service.borrow(member, id, 14);
                Item item = loan.getItem();
                System.out.println(item.describe() + " -> " + item.fee(loan.getDays()));
            } catch (LoanException e) {
                System.out.println("refused: " + e.getMessage());
            }
        }
    }
}
