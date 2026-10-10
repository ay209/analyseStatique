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
 * Options (n'importe où après l'action) :
 *   --cha                (graph, dot, callers, callees) ajoute les arcs vers les redéfinitions connues
 *   --package P          (dot) ne dessine que les méthodes des types du paquetage P
 *   --focus METHODE      (dot) ne dessine que METHODE, ses appelants et ses appeles
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

            Options (bonus) :
              --cha               graph, dot, callers, callees : ajoute les arcs vers les redefinitions
                                  connues dans les sous-types (appels virtuels, approximation de CHA)
              --package P         dot : ne dessine que les methodes des types du paquetage P
              --focus METHODE     dot : ne dessine que METHODE, ses appelants et ses appeles

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
        Options options = new Options();
        List<String> rest = options.extract(args.subList(2, args.size()));
        options.check(action);

        switch (action) {
            case METRICS:
                if (rest.isEmpty()) {
                    throw usage("Le seuil X est requis : metrics X (par exemple metrics 5).");
                }
                requireNoExtra(rest, 1);
                return new Command(project, action, threshold(rest.get(0)), null, false, null, null);
            case CALLERS:
            case CALLEES:
                if (rest.isEmpty()) {
                    throw usage("Il manque la methode : " + action.keyword() + " METHODE.");
                }
                requireNoExtra(rest, 1);
                return new Command(project, action, 0, rest.get(0), options.cha, null, null);
            default:
                requireNoExtra(rest, 0);
                return new Command(project, action, 0, null, options.cha, options.packageFilter, options.focus);
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

    /** Les options « --xxx » : extraites de la liste, puis vérifiées pour l'action choisie. */
    private static final class Options {
        private boolean cha;
        private String packageFilter;
        private String focus;

        List<String> extract(List<String> tokens) {
            List<String> positional = new java.util.ArrayList<>();
            for (int i = 0; i < tokens.size(); i++) {
                String token = tokens.get(i);
                if (!token.startsWith("--")) {
                    positional.add(token);
                } else if (token.equals("--cha")) {
                    cha = true;
                } else if (token.equals("--package") || token.equals("--focus")) {
                    if (i + 1 >= tokens.size() || tokens.get(i + 1).startsWith("--")) {
                        throw usage("L'option " + token + " attend une valeur.");
                    }
                    String value = tokens.get(++i);
                    if (token.equals("--package")) {
                        packageFilter = value;
                    } else {
                        focus = value;
                    }
                } else {
                    throw usage("Option inconnue : '" + token + "'.");
                }
            }
            return positional;
        }

        void check(Action action) {
            boolean graphLike = action == Action.GRAPH || action == Action.DOT
                    || action == Action.CALLERS || action == Action.CALLEES;
            if (cha && !graphLike) {
                throw usage("L'option --cha ne s'applique pas a l'action '" + action.keyword() + "'.");
            }
            if ((packageFilter != null || focus != null) && action != Action.DOT) {
                throw usage("Les options --package et --focus ne s'appliquent qu'a l'action 'dot'.");
            }
        }
    }

    private static CliException usage(String message) {
        return new CliException(Cli.USAGE_ERROR, message);
    }
}
