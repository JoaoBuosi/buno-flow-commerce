# One-shot E2E: dependency image compiles tests, runtime is PostgreSQL 17 on loopback.
# No production database URL is mounted here; the harness forces its own localhost DB.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
RUN mvn -q -B -DskipTests dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests test-compile

FROM postgres:17-bookworm
COPY --from=build /opt/java/openjdk /opt/java/openjdk
COPY --from=build /usr/share/maven /usr/share/maven
COPY --from=build /root/.m2 /var/lib/postgresql/.m2
COPY --from=build /workspace /opt/qa/workspace
COPY scripts/qa_real_recovery/run.sh /opt/qa/run.sh
RUN chown -R postgres:postgres /opt/qa /var/lib/postgresql/.m2 \
    && chmod 0700 /opt/qa/run.sh
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=/opt/java/openjdk/bin:/usr/share/maven/bin:/usr/lib/postgresql/17/bin:$PATH
ENV HOME=/var/lib/postgresql
ENV MAVEN_OPTS=-Dmaven.repo.local=/var/lib/postgresql/.m2/repository
USER postgres
WORKDIR /opt/qa/workspace
CMD ["bash", "/opt/qa/run.sh"]
