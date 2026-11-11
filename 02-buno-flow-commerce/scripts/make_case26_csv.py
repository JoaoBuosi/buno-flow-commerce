#!/usr/bin/env python3
"""Generate synthetic CASE26 demonstration orders. No credentials or live API calls.

Each invocation uses a unique batch ID to avoid repeated order_key collisions
during rehearsals against one authorized staging demo store.
"""
from __future__ import annotations

import argparse
import csv
import re
import secrets
from pathlib import Path

COLUMNS = ("order_key", "customer", "sku", "description", "quantity", "price")
ROWS = (
    ("Cliente Exemplo", "CAMISETA-C26", "Camiseta demonstrativa", "2", "39.90"),
    ("Cliente Demonstração", "CANECA-C26", "Caneca demonstrativa", "1", "25.00"),
    ("Cliente Teste", "BOLSA-C26", "Bolsa demonstrativa", "1", "79.90"),
)


def generate(day: int, batch: str, directory: Path) -> Path:
    if day not in (26, 27):
        raise ValueError("O dia precisa ser 26 ou 27.")
    if not re.fullmatch(r"[A-Za-z0-9]{4,20}", batch):
        raise ValueError("O identificador do ensaio deve conter de 4 a 20 letras/números.")
    directory.mkdir(parents=True, exist_ok=True)
    target = directory / f"case26-dia-{day}-{batch}.csv"
    # Exclusive creation prevents accidentally overwriting a prior rehearsal.
    with target.open("x", encoding="utf-8", newline="") as out:
        writer = csv.writer(out, lineterminator="\n")
        writer.writerow(COLUMNS)
        for number, (customer, sku, description, quantity, price) in enumerate(ROWS, 1):
            writer.writerow((f"CASE26-{day}-{batch}-{number:03}", customer, sku,
                             description, quantity, price))
    return target


def main() -> None:
    parser = argparse.ArgumentParser(description="Gera arquivo CSV fictício para o CASE26.")
    parser.add_argument("--day", type=int, choices=(26, 27), required=True)
    parser.add_argument("--batch", default=None, help="ID opcional para um ensaio (letras e números)")
    parser.add_argument("--output-dir", type=Path, default=Path("case26-output"))
    args = parser.parse_args()
    batch = args.batch or secrets.token_hex(4).upper()
    path = generate(args.day, batch, args.output_dir)
    print(f"CSV fictício preparado: {path} (3 pedidos; nenhuma integração externa)")


if __name__ == "__main__":
    main()
