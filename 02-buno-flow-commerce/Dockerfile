# One-shot QA image. The database is initialized inside the container and discarded.
# No Supabase credentials, no existing Railway volumes and no inbound public domain.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM postgres:17-bookworm
RUN apt-get update -qq && apt-get install -y -qq --no-install-recommends python3 ca-certificates >/dev/null \
  && rm -rf /var/lib/apt/lists/*
COPY --from=build /opt/java/openjdk /opt/java/openjdk
COPY --from=build /build/target/*.jar /opt/qa/app.jar
COPY scripts/qa_owner_mfa/run.sh /opt/qa/run.sh
COPY scripts/qa_owner_mfa/verify_owner_mfa.py /opt/qa/verify_owner_mfa.py
RUN chown -R postgres:postgres /opt/qa && chmod 0700 /opt/qa/run.sh
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:/usr/lib/postgresql/17/bin:$PATH
USER postgres
WORKDIR /opt/qa
CMD ["bash","/opt/qa/run.sh"]
