# Short-lived HTTP QA job. No database connection or public listener.
FROM python:3.12-slim
WORKDIR /app
COPY scripts/qa_owner_mfa/verify_staging_pilot.py /app/verify.py
USER 65534:65534
CMD ["python","-u","/app/verify.py"]
