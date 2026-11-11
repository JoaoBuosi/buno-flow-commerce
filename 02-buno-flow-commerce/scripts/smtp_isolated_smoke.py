#!/usr/bin/env python3
"""Isolated SMTP smoke test for Flow Commerce. Does not contact the app or database.

Usage (with credentials in process environment, never committed):
  python scripts/smtp_isolated_smoke.py --check
  python scripts/smtp_isolated_smoke.py --send-to controlled@example.com --confirm-send

Default: only validate configuration; --check authenticates without sending.
An actual email needs both --send-to and --confirm-send.
"""
import argparse
import os
import smtplib
import ssl
from email.message import EmailMessage

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Connect via STARTTLS and authenticate, without sending")
    parser.add_argument("--send-to", help="Controlled test mailbox; only sends with --confirm-send")
    parser.add_argument("--confirm-send", action="store_true", help="Explicitly authorize one test message")
    args = parser.parse_args()
    if args.confirm_send and not args.send_to:
        parser.error("--confirm-send requires --send-to")
    if args.send_to and not args.confirm_send:
        parser.error("sending requires --confirm-send")
    host = os.getenv("SMTP_HOST", "").strip()
    username = os.getenv("SMTP_USERNAME", "").strip()
    password = os.getenv("SMTP_PASSWORD", "")
    sender = os.getenv("COMMERCE_MAIL_FROM", "").strip()
    port = int(os.getenv("SMTP_PORT", "587"))
    if not host or not username or not password or not sender:
        parser.error("Missing SMTP_HOST, SMTP_USERNAME, SMTP_PASSWORD or COMMERCE_MAIL_FROM")
    if port != 587 or os.getenv("SMTP_STARTTLS", "true").lower() != "true":
        parser.error("Only SMTP port 587 with required STARTTLS is allowed")
    if not args.check and not args.confirm_send:
        print("Config present. No connection or message sent. Use --check to authenticate.")
        return
    if args.send_to and ("@" not in args.send_to or any(c in args.send_to for c in "\r\n")):
        parser.error("Invalid test recipient")
    try:
        with smtplib.SMTP(host, port, timeout=12) as client:
            client.ehlo()
            client.starttls(context=ssl.create_default_context())
            client.ehlo()
            client.login(username, password)
            if args.confirm_send:
                msg = EmailMessage()
                msg["From"] = sender
                msg["To"] = args.send_to
                msg["Subject"] = "Buno Flow Commerce - teste SMTP isolado"
                msg.set_content("Teste controlado de entrega SMTP do staging. Nenhum link ou dado de cliente.")
                client.send_message(msg)
                print("SMTP accepted one test message; confirm mailbox delivery separately.")
            else:
                print("SMTP STARTTLS and authentication succeeded. No message sent.")
    except (smtplib.SMTPException, OSError, ssl.SSLError):
        raise SystemExit("SMTP validation failed. Check credentials, provider restrictions and network; no secrets logged.")

if __name__ == "__main__":
    main()
