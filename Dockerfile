FROM node:22-alpine AS frontend
WORKDIR /build
COPY frontend/package.json frontend/yarn.lock ./
RUN yarn install --frozen-lockfile
COPY frontend/ ./
RUN yarn build

FROM eclipse-temurin:21-jdk-alpine AS backend
WORKDIR /build
COPY backend/gradle ./gradle
COPY backend/gradlew backend/settings.gradle.kts backend/build.gradle.kts ./
RUN ./gradlew dependencies --no-daemon --quiet || true
COPY backend/src ./src
COPY --from=frontend /build/dist/music-library-web/browser ./src/main/resources/static
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=backend /build/build/libs/*.jar app.jar
RUN mkdir -p /var/lib/musiclibrary/media && chown -R app:app /var/lib/musiclibrary
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
