package hai913i.tp1;

import hai913i.tp1.cli.Cli;

/**
 * Point d'entrée : délègue entièrement à {@link Cli}.
 * Usage : java -jar target/hai913i-tp1-analyzer.jar --help
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(new Cli(System.out, System.err).run(args));
    }
}
