"""Offline tests for CASE26 synthetic CSV fixture generator."""
import csv
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from make_case26_csv import COLUMNS, generate


class Case26CsvTests(unittest.TestCase):
    def test_columns_and_fictitious_orders_for_both_days(self):
        with TemporaryDirectory() as tmp:
            for day in (26, 27):
                path = generate(day, "ENSAIO123", Path(tmp))
                with path.open(encoding="utf-8", newline="") as stream:
                    reader = csv.DictReader(stream)
                    self.assertEqual(tuple(reader.fieldnames), COLUMNS)
                    rows = list(reader)
                self.assertEqual(len(rows), 3)
                self.assertEqual(len({r["order_key"] for r in rows}), 3)
                self.assertTrue(all(f"CASE26-{day}-ENSAIO123-" in r["order_key"] for r in rows))
                self.assertTrue(all(r["customer"].startswith("Cliente ") for r in rows))
                self.assertTrue(all(float(r["price"]) > 0 for r in rows))

    def test_batches_never_share_external_keys(self):
        with TemporaryDirectory() as tmp:
            base = Path(tmp)
            one = generate(26, "ENSAIO111", base)
            two = generate(26, "ENSAIO222", base)
            with one.open(encoding="utf-8") as f1, two.open(encoding="utf-8") as f2:
                keys1 = {r["order_key"] for r in csv.DictReader(f1)}
                keys2 = {r["order_key"] for r in csv.DictReader(f2)}
            self.assertFalse(keys1 & keys2)

    def test_invalid_batch_or_day_rejected(self):
        with TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):
                generate(25, "ENSAIO111", Path(tmp))
            for batch in ("", "../bad", "bad space", "x" * 21):
                with self.subTest(batch=batch), self.assertRaises(ValueError):
                    generate(26, batch, Path(tmp))

    def test_never_overwrites_previous_fixture(self):
        with TemporaryDirectory() as tmp:
            generate(26, "ENSAIO123", Path(tmp))
            with self.assertRaises(FileExistsError):
                generate(26, "ENSAIO123", Path(tmp))


if __name__ == "__main__":
    unittest.main()
