FROM eclipse-temurin:17-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 wetalk \
    && useradd --uid 10001 --gid wetalk --no-create-home wetalk \
    && mkdir -p /app /data/wetalk \
    && chown -R wetalk:wetalk /app /data/wetalk
WORKDIR /app
COPY --chown=wetalk:wetalk target/wetalk.jar /app/wetalk.jar
ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true -Duser.timezone=Asia/Shanghai -XX:MaxRAMPercentage=65.0"
USER wetalk
EXPOSE 5050 5051
HEALTHCHECK --interval=30s --timeout=5s --start-period=90s --retries=3 \
    CMD curl --fail --silent http://127.0.0.1:5050/api/actuator/health/readiness || exit 1
ENTRYPOINT ["java", "-jar", "/app/wetalk.jar"]
