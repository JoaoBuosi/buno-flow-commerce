FROM postgres:17-bookworm
RUN apt-get update && apt-get install -y --no-install-recommends python3 ca-certificates \
    && rm -rf /var/lib/apt/lists/*
USER postgres
WORKDIR /opt/flow-qa
COPY --chown=postgres:postgres verify_isolated_restore.py ./verify_isolated_restore.py
COPY --chown=postgres:postgres run_readonly_restore.py ./run_readonly_restore.py
CMD ["python3", "/opt/flow-qa/run_readonly_restore.py"]
