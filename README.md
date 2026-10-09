# HAI913I - TP1 - Analyse statique (Universite de Montpellier, 2026-2027)

Analyseur statique de projets Java fonde sur Eclipse JDT Core 3.46.0 : extraction de la structure et des appels,
13 metriques, graphe d'appel pondere. Binome : *(a completer)*.

## Environnement

| Element | Version |
|---|---|
| JDK | 17 minimum (teste avec 17, 21 et 25) |
| Maven | 3.9.6 ou plus recent |
| Eclipse JDT Core | 3.46.0 (telecharge par Maven) |
| JUnit | 5.14.4 |
| IDE | aucun requis ; le projet se construit en ligne de commande |

## Construction (depuis une copie propre, sans IDE)

```
git clone <URL-DU-DEPOT> tp1 && cd tp1
mvn clean package          # compile, execute les tests, produit target/hai913i-tp1-analyzer.jar
```

`mvn clean verify` execute en plus les tests de bout en bout sur le JAR (`CliIT`).

## Utilisation

```
java -jar target/hai913i-tp1-analyzer.jar PROJET ACTION [ARGUMENT]
```

| Action | Resultat |
|---|---|
| `structure` | types, attributs, methodes (avec lignes de corps), totaux |
| `calls` | appels : ligne, type statique du receveur, cible, interne / externe / non resolu |
| `metrics X` | les 13 metriques ; `X` (entier >= 0) est le seuil de la question 11 |
| `graph` | graphe d'appel : statistiques et arcs ponderes |
| `dot` | graphe d'appel au format Graphviz (`... dot > g.dot && dot -Tpng g.dot -o g.png`) |
| `callers METHODE` | qui appelle `METHODE` |
| `callees METHODE` | ce qu'appelle `METHODE` |

`METHODE` : identifiant complet (`library.model.Item#getTitle()`) ou `Type#methode` (`Item#checkOut`).
`PROJET` : dossier du projet ; les sources sont cherchees dans `src/main/java`, sinon `src`, sinon le dossier lui-meme.
Les chemins avec espaces sont acceptes (les mettre entre guillemets). `--help` affiche l'aide.

Les resultats vont sur la sortie standard ; erreurs et avertissements sur la sortie d'erreur.

| Code | Sens |
|---|---|
| 0 | succes (meme si certains fichiers contiennent des erreurs de compilation : elles sont signalees) |
| 1 | analyse impossible : dossier inexistant, pas un dossier, aucun fichier Java, methode inconnue |
| 2 | arguments invalides : action inconnue, `X` absent ou mal forme (`abc`, `-1`, `5.5`...) |
| 3 | erreur interne inattendue (message court, jamais de pile) |

### Cas d'erreur

```
$ java -jar target/hai913i-tp1-analyzer.jar /nope structure
Le dossier n'existe pas : /nope                      # code 1
$ java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation metrics abc
Le seuil X doit etre un entier positif ou nul : 'abc'.   # code 2, suivi du message d'usage
```

Avec une erreur de syntaxe dans un fichier, l'outil avertit sur stderr
(`Avertissement : 1 erreur(s) de compilation dans 1 fichier(s)...`) et analyse les autres fichiers.
Un appel dont la cible est introuvable (dependance manquante) est conserve et marque `[non resolu]`.

## Reproduire les points de controle (projet de validation)

Le projet de validation est dans `src/test/resources/validation`.

```
V=src/test/resources/validation
J="java -jar target/hai913i-tp1-analyzer.jar"
$J $V structure      # A0/A2 : 14 fichier(s), 15 type(s), 59 methode(s), 493 ligne(s) ; aucun avertissement = 0 erreur
$J $V calls          # A3    : 94 appel(s), aucun [non resolu]
$J $V metrics 5      # B2    : 15 ; 493 ; 59 ; 4 ; 3,93 ; 4,64 ; 1,40 ; Item/Member/LoanService ; ...
$J $V metrics 4      # B2    : Q11 -> Book, Item, Member, Catalog, LoanService, TextUtils
$J $V graph          # B3    : 59 noeuds, 44 arcs, 46 internes, 48 externes, 0 non resolu
$J $V callers Loanable#checkOut   # B3 : LoanService#borrow seulement
$J $V callers Item#checkOut       # B3 : (aucun)
```

## Tests et qualite

| Commande | Effet |
|---|---|
| `mvn clean test` | ~200 tests JUnit (validation, cas limites, proprietes aleatoires, robustesse, encodage) |
| `mvn clean verify -Pcoverage` | rapport JaCoCo : `target/site/jacoco/index.html` (seuil 70 % des lignes) |
| `mvn clean verify -Pquality` | Checkstyle et SpotBugs (rapports, ne casse pas le build) |
| `mvn clean -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage` | tests de mutation PIT : `target/pit-reports/index.html` |
| `mvn test -Dperf=true` | test de croissance (500 vs 2000 classes) |
| `mvn test -Drobustness.dir=CHEMIN` | echantillons de robustesse supplementaires |

Integration continue : `.github/workflows/ci.yml` (tests JDK 17/21/25, qualite, JAR, image Docker).

### Docker

```
docker build -t hai913i-analyzer .
docker run --rm -v "$PWD/src/test/resources/validation:/project:ro" hai913i-analyzer /project metrics 5
```

## Architecture

```
Main -> cli.Cli -> cli.CommandLine (arguments) -> cli.Analysis -> parse (JDT) -> extract (visiteurs) -> model (faits)
                                                                 \-> metrics.ProjectMetrics  \-> graph.CallGraph
                -> cli.Report (affichage)
```

Seuls `parse` et `extract` touchent JDT ; `model`, `metrics`, `graph` et `cli` n'en dependent pas.
Conventions et choix : voir le compte-rendu (`rapport.pdf`).

## Usage de l'IA

Declare dans le compte-rendu, section « Utilisation des outils d'intelligence artificielle ».
