# Khabar's backend in one Hugging Face Docker Space: the Java API on the public port (7860) and the Python
# agents beside it on localhost. Built from the files publish.sh copies in from services/.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY api/pom.xml .
RUN mvn -q -B dependency:go-offline
COPY api/src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends python3 python3-venv \
    && rm -rf /var/lib/apt/lists/*
# Spaces run the container as user 1000.
RUN id -u 1000 >/dev/null 2>&1 || useradd -m -u 1000 user
WORKDIR /app
COPY agents/requirements.txt agents/requirements.txt
RUN python3 -m venv /opt/agents && /opt/agents/bin/pip install --no-cache-dir -r agents/requirements.txt
COPY agents ./agents
COPY --from=build /build/target/khabar-api-0.0.1-SNAPSHOT.jar api.jar
COPY start.sh .
RUN chown -R 1000:1000 /app
USER 1000
ENV PORT=7860 \
    SPRING_PROFILES_ACTIVE=local,demo \
    AGENTS_SERVICE_URL=http://127.0.0.1:8000 \
    KHABAR_TIME_ZONE=Asia/Kuala_Lumpur
EXPOSE 7860
CMD ["sh", "start.sh"]
