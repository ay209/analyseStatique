package hai913i.tp1.cli;

/** Une erreur prévue, avec le code de sortie à renvoyer. Jamais de pile affichée à l'utilisateur. */
public final class CliException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int exitCode;

    public CliException(int exitCode, String message) {
        super(message);
        this.exitCode = exitCode;
    }

    public int exitCode() {
        return exitCode;
    }
}
