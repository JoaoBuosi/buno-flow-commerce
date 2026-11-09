import tempfile
import unittest
from pathlib import Path

import pandas as pd

from train_demand import train, load_retail, build_features


class DemandPipelineTest(unittest.TestCase):
    def test_filters_cancellations_and_negative_lines(self):
        with tempfile.TemporaryDirectory() as tmp:
            f = Path(tmp) / "orders.csv"
            pd.DataFrame([
                {"InvoiceNo": "123", "StockCode": "SKU1", "Quantity": 2, "InvoiceDate": "2011-01-03", "UnitPrice": 5},
                {"InvoiceNo": "C124", "StockCode": "SKU1", "Quantity": 2, "InvoiceDate": "2011-01-03", "UnitPrice": 5},
                {"InvoiceNo": "125", "StockCode": "SKU1", "Quantity": -2, "InvoiceDate": "2011-01-03", "UnitPrice": 5},
            ]).to_csv(f, index=False)
            data = load_retail(f)
            self.assertEqual(1, len(data))
            self.assertEqual(2, data["Quantity"].iloc[0])
            self.assertNotIn("CustomerID", data.columns)

    def test_weekly_training_is_time_separated(self):
        weeks = pd.date_range("2010-12-05", periods=34, freq="W-SUN")
        data = pd.DataFrame(
            [{"week": w, "StockCode": sku, "Quantity": 3 + (i % 4)}
             for sku in ["SKU-A", "SKU-B"] for i, w in enumerate(weeks)]
        )
        train_set, test, *_ = build_features(data, top_skus=2, test_weeks=8)
        self.assertLess(train_set["week"].max(), test["week"].min())

    def test_output_has_no_customer_records(self):
        weeks = pd.date_range("2010-12-05", periods=34, freq="W-SUN")
        lines = [{"InvoiceNo": str(100 + i), "StockCode": sku, "Quantity": 5 + i % 3,
                  "InvoiceDate": w, "UnitPrice": 12.0, "CustomerID": "private-123"}
                 for sku in ["SKU-A", "SKU-B"] for i, w in enumerate(weeks)]
        with tempfile.TemporaryDirectory() as tmp:
            p = Path(tmp) / "history.csv"
            pd.DataFrame(lines).to_csv(p, index=False)
            train(p, Path(tmp) / "output")
            out = (Path(tmp) / "output" / "model.json").read_text()
            self.assertNotIn("private-123", out)


if __name__ == "__main__":
    unittest.main()
