FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

FROM eclipse-temurin:21-jdk
WORKDIR /app
COPY --from=build /src/target/jobscout-0.1.0.jar app.jar
# Install Chromium + OS dependencies for Playwright using the bundled CLI.
RUN java -Dloader.main=com.microsoft.playwright.CLI -cp app.jar \
    org.springframework.boot.loader.launch.PropertiesLauncher install --with-deps chromium
COPY config ./config
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
