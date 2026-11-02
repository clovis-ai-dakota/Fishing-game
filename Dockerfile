# Deep Line online battle server.
#   docker build -t deepline-server .
#   docker run -p 8080:8080 deepline-server
# The server listens on $PORT (default 8080); players connect to ws(s)://<host>/battle.
FROM gradle:8.14.3-jdk17 AS build
WORKDIR /src
COPY . .
RUN gradle :server:installDist --no-daemon

FROM eclipse-temurin:17-jre
COPY --from=build /src/server/build/install/server /app
ENV PORT=8080
EXPOSE 8080
CMD ["/app/bin/server"]
