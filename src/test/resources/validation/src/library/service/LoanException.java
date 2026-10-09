package library.service;

/**
 * Raised when a loan is refused.
 */
public class LoanException extends Exception {

    public LoanException(String message) {
        super(message);
    }
}
