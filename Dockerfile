FROM gradle:8.5-jdk21 AS build
WORKDIR /app
COPY . .

# Clean previous builds
RUN ./gradlew clean --no-daemon

# Build JS webapp for production (no HMR/dev server code)
RUN ./gradlew composeApp:jsBrowserDistribution --no-daemon

# List what was built (for debugging)
RUN ls -la composeApp/build/dist/js/productionExecutable/ || echo "No productionExecutable"

# Clear old web resources and copy new distribution
RUN rm -f server/src/main/resources/web/*.js server/src/main/resources/web/*.wasm
RUN cp -r composeApp/build/dist/js/productionExecutable/* server/src/main/resources/web/

# Build server JAR (now includes the JS app)
RUN ./gradlew server:shadowJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/server/build/libs/server-all.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
