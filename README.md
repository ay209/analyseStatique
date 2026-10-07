# HAI913I — TP1 Analyse statique — squelette de départ

Squelette Maven **facultatif** pour démarrer le TP1. Il ne contient **aucune** partie évaluée :
ni extraction de structure, ni appels, ni métriques, ni graphe d'appel. Il fournit seulement un
environnement qui compile, une configuration `ASTParser` qui résout les bindings, et un test de
fumée correspondant au **point de contrôle A0**.

Vous pouvez aussi partir de zéro : dans ce cas, reprenez au minimum les réglages de
`JdtParser` et vérifiez-les avec le projet de validation.

## Versions testées

| Élément          | Version                                             |
|------------------|-----------------------------------------------------|
| JDK              | 17 (minimum) — testé avec 17.0.10 et 21.0.5         |
| Maven            | 3.9.6                                               |
| Eclipse JDT Core | `org.eclipse.jdt:org.eclipse.jdt.core:3.46.0`       |
| JUnit Jupiter    | 5.14.4                                              |

## Commandes

```text
mvn clean test          # compile et lance le test de fumée (A0)
mvn clean package       # produit target/hai913i-tp1-analyzer.jar
java -jar target/hai913i-tp1-analyzer.jar ../resources/validation
```

Sous Windows, entourez de guillemets les chemins qui contiennent des espaces :

```text
java -jar target\hai913i-tp1-analyzer.jar "C:\Mes cours\HAI913I\validation"
```

Sortie attendue sur le projet de validation :

```text
Racine des sources     : ...\validation\src
Unites de compilation  : 14
Erreurs de compilation : 0
```

## Organisation

```text
src/main/java/hai913i/tp1/Main.java               point d'entrée (lit les arguments, délègue)
src/main/java/hai913i/tp1/parse/ProjectSources.java   racine des sources et fichiers .java
src/main/java/hai913i/tp1/parse/JdtParser.java        configuration d'ASTParser (bindings)
src/test/java/hai913i/tp1/ParserSmokeTest.java        point de contrôle A0
src/test/resources/sample/                            mini-projet de deux fichiers
```

Ajoutez vos propres paquetages (par exemple `model`, `extraction`, `metrics`, `callgraph`,
`report`) : l'organisation est libre, mais elle doit rester lisible et justifiée dans le compte rendu.

Ce `README.md` décrit le squelette ; le README demandé dans le livrable est **le vôtre** et doit
décrire votre analyseur (voir le sujet, section « Livrables »).
