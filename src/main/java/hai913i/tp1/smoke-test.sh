#!/usr/bin/env bash
# Test de recette : rejoue les commandes du README et les questions du TP1.
# Usage (depuis starter/) :  bash smoke-test.sh [chemin/robustness]
#   - sans argument : saute les echantillons de robustesse
#   - ajouter  FULL=1  pour relancer aussi 'mvn clean verify' (long)
set -u
JAR=target/hai913i-tp1-analyzer.jar
V=src/test/resources/validation
R="${1:-}"
OK=0; KO=0

# check "nom" code_attendu "motif attendu dans la sortie (ou rien)" commande...
check() {
  local name="$1" want="$2" pat="$3"; shift 3
  local out code
  out=$("$@" 2>&1); code=$?
  if [ "$code" -eq "$want" ] && { [ -z "$pat" ] || printf '%s' "$out" | grep -q -E -- "$pat"; }; then
    printf 'OK   %s\n' "$name"; OK=$((OK+1))
  else
    printf 'KO   %s  (code %s, attendu %s, motif "%s")\n' "$name" "$code" "$want" "$pat"
    printf '%s\n' "$out" | head -5 | sed 's/^/       /'
    KO=$((KO+1))
  fi
}
an() { java -jar "$JAR" "$@"; }

if [ "${FULL:-0}" = "1" ]; then
  echo "== Build complet =="
  mvn -B -ntp clean verify -Pcoverage,quality 2>&1 | grep -E "Tests run:.*Skipped: [0-9]+$|All coverage|BugInstance|BUILD"
fi

[ -f "$JAR" ] || { echo "Jar absent : lancez  mvn clean package  d'abord"; exit 1; }
[ -d "$V" ]  || { echo "Projet de validation absent : $V"; exit 1; }

echo "== Actions du TP (projet de validation) =="
check "structure"                    0 "library.service.Catalog"      an "$V" structure
check "calls (interne/externe)"      0 "\[interne\]"                  an "$V" calls
check "metrics 5 : Q1 = 15 classes"  0 "Q1 .*: 15"                    an "$V" metrics 5
check "metrics 5 : Q13 present"      0 "Q13"                          an "$V" metrics 5
check "graph"                        0 "Arcs"                         an "$V" graph
check "graph : arc pondere (xN)"     0 " x[0-9]"                      an "$V" graph
check "graph --cha"                  0 "Mode CHA"                     an "$V" graph --cha
check "dot"                          0 "digraph"                      an "$V" dot
check "dot --package"                0 "digraph"                      an "$V" dot --package library.service
check "dot --focus --cha"            0 "digraph"                      an "$V" dot --focus 'LoanService#borrow' --cha
check "callers"                      0 "Appelants de"                 an "$V" callers 'LoanService#borrow'
check "callees"                      0 "Appeles par"                  an "$V" callees 'LoanService#borrow'
check "callers --cha"                0 "Appelants de"                 an "$V" callers 'LoanService#borrow' --cha
check "spoon (bonus C1)"             0 "Comparaison JDT / Spoon"      an "$V" spoon

echo "== Surcharges : Catalog#add distinguees =="
check "deux add differents"          0 "Catalog#add\(library.model.Item\)" an "$V" calls

echo "== Erreurs et codes de sortie =="
check "aucun argument -> 2"          2 "Usage"                        an
check "action manquante -> 2"        2 "Action manquante"             an "$V"
check "action inconnue -> 2"         2 ""                             an "$V" inconnue
check "chemin inexistant -> 1"       1 ""                             an /chemin/inexistant structure

if [ -n "$R" ] && [ -d "$R" ]; then
  echo "== Robustesse (codes affiches, a verifier a la main) =="
  for d in "$R"/*/; do
    n=$(basename "$d"); an "$d" structure >/dev/null 2>&1; echo "     $n -> code $?"
  done
fi

if command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1; then
  echo "== Docker =="
  docker build -q -t hai913i-analyzer . >/dev/null 2>&1
  check "docker run metrics 5"       0 "Q1 .*: 15"  docker run --rm -v "$PWD/$V:/project:ro" hai913i-analyzer /project metrics 5
else
  echo "== Docker : saute (Docker Desktop non demarre) =="
fi

echo
echo "Resultat : $OK reussi(s), $KO echec(s)"
[ "$KO" -eq 0 ]
