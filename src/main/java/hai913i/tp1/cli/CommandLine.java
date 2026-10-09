package hai913i.tp1.cli;

import java.util.List;

import hai913i.tp1.cli.Command.Action;

/**
 * Lecture et validation des arguments, sans rien exécuter (testable seule).
 *
 * Syntaxe : PROJET ACTION [ARGUMENT]
 *   structure | calls | graph | dot         (pas d'argument)
 *   metrics X                               (X entier >= 0 : seuil de la question 11)
 *   callers METHODE | callees METHODE
 */
public final class CommandLine {

    public static final String USAGE = """
            Usage : java -jar hai913i-tp1-analyzer.jar PROJET ACTION [ARGUMENT]

            PROJET   dossier du projet Java a analyser (src/main/java, sinon src, sinon le dossier lui-meme)

            Actions :
              structure           types, attributs et methodes (avec le nombre de lignes de chaque corps)
              calls               appels de methodes : ligne, type du receveur, cible, interne/externe/non resolu
              metrics X           les 13 metriques ; X (entier >= 0) est le seuil de la question 11
              graph               graphe d'appel : statistiques et arcs ponderes
              dot                 graphe d'appel au format Graphviz DOT
              callers METHODE     qui appelle METHODE
              callees METHODE     ce qu'appelle METHODE
            METHODE : identifiant complet, ou Type#methode (ex. Item#checkOut).

            Resultats sur la sortie standard ; erreurs et avertissements sur la sortie d'erreur.
            Codes de sortie : 0 succes, 1 analyse impossible, 2 arguments invalides, 3 erreur interne.
            """;

    private CommandLine() {
    }

    public static Command parse(List<String> args) {
        if (args.isEmpty()) {
            throw usage("Arguments manquants.");
        }
        if (args.size() < 2) {
            throw usage("Action manquante.");
        }
        String project = args.get(0);
        Action action = Action.of(args.get(1));
        if (action == null) {
            throw usage("Action inconnue : '" + args.get(1) + "'.");
        }
        List<String> rest = args.subList(2, args.size());

        switch (action) {
            case METRICS:
                if (rest.isEmpty()) {
                    throw usage("Le seuil X est requis : metrics X (par exemple metrics 5).");
                }
                requireNoExtra(rest, 1);
                return new Command(project, action, threshold(rest.get(0)), null);
            case CALLERS:
            case CALLEES:
                if (rest.isEmpty()) {
                    throw usage("Il manque la methode : " + action.keyword() + " METHODE.");
                }
                requireNoExtra(rest, 1);
                return new Command(project, action, 0, rest.get(0));
            default:
                requireNoExtra(rest, 0);
                return new Command(project, action, 0, null);
        }
    }

    private static int threshold(String text) {
        try {
            int x = Integer.parseInt(text.trim());
            if (x >= 0) {
                return x;
            }
        } catch (NumberFormatException e) {
            // message ci-dessous
        }
        throw usage("Le seuil X doit etre un entier positif ou nul : '" + text + "'.");
    }

    private static void requireNoExtra(List<String> rest, int expected) {
        if (rest.size() > expected) {
            throw usage("Arguments en trop : " + String.join(" ", rest.subList(expected, rest.size())));
        }
    }

    private static CliException usage(String message) {
        return new CliException(Cli.USAGE_ERROR, message);
    }
}
