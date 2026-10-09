package hai913i.tp1.cli;

/**
 * Une commande entièrement validée : le projet à analyser, l'action, et ses paramètres éventuels.
 * {@code threshold} n'a de sens que pour METRICS, {@code method} que pour CALLERS et CALLEES.
 */
public record Command(String project, Action action, int threshold, String method) {

    /** Ce que l'outil sait produire. */
    public enum Action {
        STRUCTURE("structure"), CALLS("calls"), METRICS("metrics"), GRAPH("graph"), DOT("dot"),
        CALLERS("callers"), CALLEES("callees");

        private final String keyword;

        Action(String keyword) {
            this.keyword = keyword;
        }

        public String keyword() {
            return keyword;
        }

        static Action of(String text) {
            for (Action a : values()) {
                if (a.keyword.equalsIgnoreCase(text)) {
                    return a;
                }
            }
            return null;
        }
    }
}
