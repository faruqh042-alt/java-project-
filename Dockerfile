FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY src ./src
RUN mkdir -p out && javac --add-modules jdk.httpserver -encoding UTF-8 -d out src/*.java

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/out ./out
COPY assets ./assets
COPY data ./seed-data
ENV PORT=8080
ENV DATA_DIR=/var/data
EXPOSE 8080
CMD ["java", "--add-modules", "jdk.httpserver", "-cp", "out", "WebMain"]
