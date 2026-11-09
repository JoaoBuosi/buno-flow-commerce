"""Research-only weekly SKU demand model; never train on identifiable customer fields.

Source: Chen, D. (2015), UCI Online Retail, CC BY 4.0.
https://doi.org/10.24432/C5BW33
"""
from __future__ import annotations

import argparse
import json
import math
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.linear_model import Ridge
from sklearn.metrics import mean_absolute_error
from sklearn.preprocessing import StandardScaler

SOURCE = "https://archive.ics.uci.edu/dataset/352/online-retail"
DOI = "https://doi.org/10.24432/C5BW33"
FEATURES = ["lag_1", "lag_2", "lag_4_avg", "lag_4_std", "week_sin", "week_cos"]


def load_retail(path: Path) -> pd.DataFrame:
    data = pd.read_excel(path, engine="openpyxl") if path.suffix.lower() in (".xlsx", ".xls") else pd.read_csv(path)
    required = {"InvoiceNo", "StockCode", "Quantity", "InvoiceDate", "UnitPrice"}
    if not required.issubset(data.columns):
        raise ValueError(f"Missing fields: {sorted(required - set(data.columns))}")
    # No CustomerID, country, location, or free-text descriptions are used or exported.
    data = data[list(required)].copy()
    data["InvoiceDate"] = pd.to_datetime(data["InvoiceDate"], errors="coerce")
    data["Quantity"] = pd.to_numeric(data["Quantity"], errors="coerce")
    data["UnitPrice"] = pd.to_numeric(data["UnitPrice"], errors="coerce")
    data = data[
        data["InvoiceDate"].notna()
        & (data["Quantity"] > 0)
        & (data["UnitPrice"] > 0)
        & (~data["InvoiceNo"].astype(str).str.upper().str.startswith("C"))
    ].copy()
    data["StockCode"] = data["StockCode"].astype(str).str.strip()
    data = data[data["StockCode"].str.len().between(2, 80)]
    data["week"] = data["InvoiceDate"].dt.to_period("W-SUN").dt.end_time.dt.normalize()
    return data


def build_features(data: pd.DataFrame, top_skus: int = 200, test_weeks: int = 8):
    dates = pd.date_range(data["week"].min(), data["week"].max(), freq="W-SUN")
    if len(dates) < test_weeks + 16:
        raise ValueError("Insufficient time history; at least 24 weeks required")
    holdout_start = dates[-test_weeks]
    train_skus = (
        data.loc[data["week"] < holdout_start].groupby("StockCode")["Quantity"]
        .sum().sort_values(ascending=False).head(top_skus).index.tolist()
    )
    selected = data[data["StockCode"].isin(train_skus)]
    weekly = selected.groupby(["StockCode", "week"])["Quantity"].sum()
    rows = []
    for sku in train_skus:
        series = weekly.loc[sku].reindex(dates, fill_value=0).to_numpy(dtype=float)
        for i in range(4, len(dates)):
            last4 = series[i - 4:i]
            week_number = int(dates[i].isocalendar().week)
            rows.append((dates[i], series[i], last4[-1], last4[-2],
                         last4.mean(), last4.std(),
                         math.sin(2 * math.pi * week_number / 52.18),
                         math.cos(2 * math.pi * week_number / 52.18)))
    frame = pd.DataFrame(rows, columns=["week", "units", *FEATURES])
    frame = frame.replace([np.inf, -np.inf], np.nan).dropna()
    train = frame[frame["week"] < holdout_start]
    test = frame[frame["week"] >= holdout_start]
    if train.empty or test.empty:
        raise ValueError("Train or test period empty")
    return train, test, str(train["week"].min().date()), str(train["week"].max().date()), str(test["week"].min().date()), str(test["week"].max().date()), len(train_skus)


def train(input_path: Path, output_dir: Path) -> dict:
    data = load_retail(input_path)
    train_data, test, train_start, train_end, test_start, test_end, sku_count = build_features(data)
    scaler = StandardScaler()
    xtrain = scaler.fit_transform(train_data[FEATURES].to_numpy())
    model = Ridge(alpha=20.0).fit(xtrain, np.log1p(train_data["units"].to_numpy()))
    xtest = scaler.transform(test[FEATURES].to_numpy())
    # Clamp extrapolation using only historical features.
    predicted_raw = np.maximum(0.0, np.expm1(np.clip(model.predict(xtest), -20, 12)))
    max_reasonable = np.maximum(
        20.0,
        np.maximum(3.0 * test["lag_4_avg"].to_numpy() + 20.0, 2.0 * test["lag_1"].to_numpy() + 20.0),
    )
    predicted = np.minimum(predicted_raw, max_reasonable)
    y = test["units"].to_numpy()
    baseline = np.maximum(0.0, test["lag_4_avg"].to_numpy())
    mae = mean_absolute_error(y, predicted)
    baseline_mae = mean_absolute_error(y, baseline)
    wape = float(np.abs(y - predicted).sum() / max(y.sum(), 1))
    improvement = float((baseline_mae - mae) / max(baseline_mae, 1e-9))
    approved = bool(improvement >= 0.05 and wape < 1.0)
    output_dir.mkdir(parents=True, exist_ok=True)
    report = {
        "source": SOURCE, "doi": DOI, "license": "CC BY 4.0",
        "training_period": [train_start, train_end],
        "holdout_period": [test_start, test_end],
        "sku_count": sku_count, "training_samples": len(train_data),
        "holdout_samples": len(test), "model_mae_units": round(float(mae), 4),
        "baseline_mae_units": round(float(baseline_mae), 4),
        "holdout_wape": round(wape, 4),
        "improvement_over_baseline": round(improvement, 4),
        "eligible_for_internal_pilot": approved,
        "external_validity": "Historic UK retailer, 2010-2011; not validated for Brazilian sellers.",
        "personal_data": "Customer identifiers and raw rows are not exported.",
    }
    artifact = {
        "format": "buno.demand.ridge.v1",
        "status": "research_candidate" if approved else "not_better_than_baseline",
        "features": FEATURES,
        "mean": [float(v) for v in scaler.mean_],
        "scale": [float(v) for v in scaler.scale_],
        "coefficients": [float(v) for v in model.coef_],
        "intercept": float(model.intercept_),
        "target": "log1p(weekly positive unit demand)",\n        "prediction_guard": "min(expm1(clamp(raw,-20,12)),max(20,3*mean4+20,2*lag1+20))",
        "provenance": {"source": SOURCE, "doi": DOI, "license": "CC BY 4.0"},
        "evaluation": report,
    }
    (output_dir / "model.json").write_text(json.dumps(artifact, indent=2, ensure_ascii=False), encoding="utf-8")
    (output_dir / "metrics.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
    return report


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--input", type=Path, required=True)
    p.add_argument("--out", type=Path, default=Path("artifacts"))
    args = p.parse_args()
    train(args.input, args.out)


if __name__ == "__main__":
    main()
