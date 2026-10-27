# syntax=docker/dockerfile:1

# Estágio 1: compila com o JDK e o Maven Wrapper do próprio projeto.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# Primeiro só o que define as dependências: enquanto o pom.xml não muda,
# esta camada (e o download das dependências) vem do cache do Docker.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
# Os testes não rodam aqui: os de integração sobem Postgres/Kafka com Testcontainers,
# o que exige Docker dentro do build. Eles rodam na máquina e na CI (Etapa 5).
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# Estágio 2: só o JRE e o jar. Sem JDK, Maven, código-fonte nem cache: imagem menor e com menos a explorar.
FROM eclipse-temurin:25-jre
RUN useradd --system --no-create-home --uid 10001 app
WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
# não-root: uma falha na aplicação não dá root dentro do container
USER app
EXPOSE 8081
# usa até 75% da memória limite do container para o heap (o padrão da JVM é 25%)
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
