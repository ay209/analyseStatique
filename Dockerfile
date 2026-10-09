# syntax=docker/dockerfile:1

# ---------- etape 1 : construire le jar ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# On copie d'abord le pom seul : tant qu'il ne change pas, la couche des dependances est reutilisee (cache Docker).
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp package -DskipTests

# ---------- etape 2 : image d'execution minimale (JRE seul, pas de Maven ni de JDK) ----------
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 analyzer
COPY --from=build /build/target/hai913i-tp1-analyzer.jar /app/analyzer.jar

USER analyzer
# Le projet a analyser est monte ici : docker run -v "/chemin/projet:/project:ro" image /project
WORKDIR /project
ENTRYPOINT ["java", "-jar", "/app/analyzer.jar"]
