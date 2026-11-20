"""Train the RiskLens credit risk model and export it for the Java backend.

Usage:  python download_data.py && python train.py

Model: logistic regression on standardized features, predicting the probability
that an applicant defaults on the loan (loan_status = 1).

Outputs
  backend/src/main/resources/model/credit_risk_model.json   model the API loads at startup
  ml/metrics.json                                           full evaluation report
  docs/model/roc_curve.png, docs/model/calibration.png      charts for the README
"""
from __future__ import annotations

import json
import math
import sys
from datetime import date
from pathlib import Path

import numpy as np
import pandas as pd
from sklearn.calibration import calibration_curve
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import brier_score_loss, confusion_matrix, roc_auc_score, roc_curve
from sklearn.model_selection import StratifiedKFold, cross_val_score, train_test_split
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import StandardScaler

ROOT = Path(__file__).resolve().parent
DATA_FILE = ROOT / "data" / "credit_risk_dataset.csv"
MODEL_OUT = ROOT.parent / "backend" / "src" / "main" / "resources" / "model" / "credit_risk_model.json"
METRICS_OUT = ROOT / "metrics.json"
PLOTS_DIR = ROOT.parent / "docs" / "model"

SEED = 42
TEST_SIZE = 0.2

# Probability-of-default bands used for the LOW / MEDIUM / HIGH label.
LOW_THRESHOLD = 0.15   # PD < 15%            -> LOW
HIGH_THRESHOLD = 0.40  # 15% <= PD < 40%     -> MEDIUM, PD >= 40% -> HIGH

HOME_OWNERSHIP = ["RENT", "OWN", "MORTGAGE", "OTHER"]
LOAN_INTENT = ["EDUCATION", "MEDICAL", "VENTURE", "PERSONAL", "DEBTCONSOLIDATION", "HOMEIMPROVEMENT"]

# Raw numeric inputs (API field names) that are clipped to the training range before scoring.
CLIPPED_INPUTS = ["age", "income", "loanAmount", "employmentYears", "creditHistoryYears"]

# Model column -> human-readable factor shown in the score breakdown.
FACTORS = {
    "age": "Age",
    "log_income": "Income",
    "loan_to_income": "Loan-to-income",
    "employment_years": "Employment length",
    "credit_history_years": "Credit history length",
    "previous_default": "Previous default",
    **{f"home_ownership={h}": "Home ownership" for h in HOME_OWNERSHIP},
    **{f"loan_intent={i}": "Loan purpose" for i in LOAN_INTENT},
}
COLUMNS = list(FACTORS)

# Loan amount enters only through loan_to_income. Using log(loan amount) as well made the three
# income/loan features collinear: the model then gave loan amount a *negative* weight, which
# accuracy didn't need and which made the per-factor explanation nonsensical.
#
# Deliberately NOT used: loan_grade and loan_int_rate. Both are set by the lender *after*
# assessing risk, so using them would leak the answer into the inputs.


# --------------------------------------------------------------------------- data

def load_and_clean() -> tuple[pd.DataFrame, dict]:
    df = pd.read_csv(DATA_FILE)
    report = {"rawRows": len(df)}

    df = df.drop_duplicates()
    report["duplicatesRemoved"] = report["rawRows"] - len(df)

    before = len(df)
    df = df[df["person_age"] <= 100]
    df = df[df["person_emp_length"].isna() | (df["person_emp_length"] <= 60)]
    report["implausibleRowsRemoved"] = before - len(df)  # e.g. age 144, 123 years employed

    report["missingEmploymentLength"] = int(df["person_emp_length"].isna().sum())
    report["cleanRows"] = len(df)
    report["defaultRate"] = round(float(df["loan_status"].mean()), 4)
    return df, report


def to_api_inputs(df: pd.DataFrame) -> pd.DataFrame:
    """Rename dataset columns to the field names the REST API receives."""
    return pd.DataFrame({
        "age": df["person_age"].astype(float),
        "income": df["person_income"].astype(float),
        "loanAmount": df["loan_amnt"].astype(float),
        "employmentYears": df["person_emp_length"].astype(float),
        "creditHistoryYears": df["cb_person_cred_hist_length"].astype(float),
        "previousDefault": df["cb_person_default_on_file"].eq("Y"),
        "homeOwnership": df["person_home_ownership"],
        "loanIntent": df["loan_intent"],
    }, index=df.index)


def design_matrix(inputs: pd.DataFrame, clip: dict[str, list[float]]) -> pd.DataFrame:
    """Feature engineering. backend RiskModel.java mirrors this exactly."""
    r = inputs.copy()
    for col, (lo, hi) in clip.items():
        r[col] = r[col].clip(lo, hi)

    x = pd.DataFrame(index=r.index)
    x["age"] = r["age"]
    x["log_income"] = np.log(r["income"])
    x["loan_to_income"] = r["loanAmount"] / r["income"]
    x["employment_years"] = r["employmentYears"]
    x["credit_history_years"] = r["creditHistoryYears"]
    x["previous_default"] = r["previousDefault"].astype(float)
    for h in HOME_OWNERSHIP:
        x[f"home_ownership={h}"] = (r["homeOwnership"] == h).astype(float)
    for i in LOAN_INTENT:
        x[f"loan_intent={i}"] = (r["loanIntent"] == i).astype(float)
    return x[COLUMNS]


# --------------------------------------------------------------------------- scoring (export math)

def sigmoid(z: float) -> float:
    return 1.0 / (1.0 + math.exp(-z))


def score_exported(model: dict, row: pd.Series) -> float:
    """Re-implements scoring from the exported JSON only, to prove the export is complete."""
    z = model["intercept"]
    for c in model["columns"]:
        z += c["coefficient"] * (row[c["name"]] - c["mean"]) / c["scale"]
    return sigmoid(z)


def band(p: float) -> str:
    return "LOW" if p < LOW_THRESHOLD else ("MEDIUM" if p < HIGH_THRESHOLD else "HIGH")


# --------------------------------------------------------------------------- main

def main() -> int:
    if not DATA_FILE.exists():
        print("Dataset missing. Run:  python download_data.py", file=sys.stderr)
        return 1

    df, data_report = load_and_clean()
    inputs = to_api_inputs(df)
    y = df["loan_status"].to_numpy()

    in_train, in_test, y_train, y_test = train_test_split(
        inputs, y, test_size=TEST_SIZE, stratify=y, random_state=SEED)

    # Impute missing employment length with the TRAINING median (no test-set leakage).
    emp_median = float(in_train["employmentYears"].median())
    in_train = in_train.fillna({"employmentYears": emp_median})
    in_test = in_test.fillna({"employmentYears": emp_median})

    clip = {c: [float(in_train[c].min()), float(in_train[c].max())] for c in CLIPPED_INPUTS}
    x_train = design_matrix(in_train, clip)
    x_test = design_matrix(in_test, clip)

    # ---- cross-validated AUC on the training set
    cv = StratifiedKFold(n_splits=5, shuffle=True, random_state=SEED)
    cv_auc = cross_val_score(
        make_pipeline(StandardScaler(), LogisticRegression(max_iter=2000)),
        x_train, y_train, cv=cv, scoring="roc_auc")

    # ---- final model
    scaler = StandardScaler().fit(x_train)
    lr = LogisticRegression(max_iter=2000).fit(scaler.transform(x_train), y_train)
    p_test = lr.predict_proba(scaler.transform(x_test))[:, 1]

    # ---- comparison models
    gbm = HistGradientBoostingClassifier(random_state=SEED).fit(x_train, y_train)
    p_gbm = gbm.predict_proba(x_test)[:, 1]
    baseline_auc = roc_auc_score(y_test, x_test["loan_to_income"])

    # ---- metrics
    auc = roc_auc_score(y_test, p_test)
    fpr, tpr, _ = roc_curve(y_test, p_test)
    tn, fp, fn, tp = confusion_matrix(y_test, (p_test >= 0.5).astype(int)).ravel()

    bands = []
    labels = np.array([band(p) for p in p_test])
    for name in ("LOW", "MEDIUM", "HIGH"):
        mask = labels == name
        bands.append({
            "label": name,
            "applicants": int(mask.sum()),
            "share": round(float(mask.mean()), 4),
            "meanPredictedPD": round(float(p_test[mask].mean()), 4) if mask.any() else None,
            "observedDefaultRate": round(float(y_test[mask].mean()), 4) if mask.any() else None,
        })

    metrics = {
        "data": data_report,
        "split": {"train": int(len(y_train)), "test": int(len(y_test)), "seed": SEED},
        "logisticRegression": {
            "testAUC": round(auc, 4),
            "gini": round(2 * auc - 1, 4),
            "ks": round(float(np.max(tpr - fpr)), 4),
            "brierScore": round(float(brier_score_loss(y_test, p_test)), 4),
            "cvAUCMean": round(float(cv_auc.mean()), 4),
            "cvAUCStd": round(float(cv_auc.std()), 4),
            "confusionMatrixAt50pct": {"tn": int(tn), "fp": int(fp), "fn": int(fn), "tp": int(tp),
                                       "precision": round(tp / (tp + fp), 4),
                                       "recall": round(tp / (tp + fn), 4)},
        },
        "riskBands": bands,
        "comparison": {
            "baselineLoanToIncomeOnlyAUC": round(float(baseline_auc), 4),
            "gradientBoostingAUC": round(float(roc_auc_score(y_test, p_gbm)), 4),
        },
    }

    # ---- export
    model = {
        "modelVersion": f"lr-{date.today().isoformat()}",
        "algorithm": "logistic_regression",
        "trainedOn": "Kaggle laotse/credit-risk-dataset",
        "target": "probability of default (loan_status = 1)",
        "intercept": float(lr.intercept_[0]),
        "columns": [
            {"name": name, "factor": FACTORS[name], "mean": float(m), "scale": float(s), "coefficient": float(c)}
            for name, m, s, c in zip(COLUMNS, scaler.mean_, scaler.scale_, lr.coef_[0])
        ],
        "clip": clip,
        "thresholds": {"low": LOW_THRESHOLD, "high": HIGH_THRESHOLD},
        "metrics": {
            "testAUC": metrics["logisticRegression"]["testAUC"],
            "gini": metrics["logisticRegression"]["gini"],
            "ks": metrics["logisticRegression"]["ks"],
            "trainingRows": int(len(y_train)),
            "testRows": int(len(y_test)),
            "defaultRate": data_report["defaultRate"],
        },
    }

    # Parity check: exported math must reproduce scikit-learn exactly.
    sk_all = lr.predict_proba(scaler.transform(x_test))[:, 1]
    for k in range(200):
        assert abs(score_exported(model, x_test.iloc[k]) - sk_all[k]) < 1e-9, "export mismatch"

    # Test vectors for the Java parity test (RiskModelTest).
    vectors = []
    for k in [0, 1, 2, 3, 4, 5, 6, 7]:
        raw = in_test.iloc[k]
        vectors.append({
            "input": {
                "age": int(raw["age"]),
                "income": float(raw["income"]),
                "loanAmount": float(raw["loanAmount"]),
                "employmentYears": int(round(raw["employmentYears"])),
                "creditHistoryYears": int(raw["creditHistoryYears"]),
                "previousDefault": bool(raw["previousDefault"]),
                "homeOwnership": str(raw["homeOwnership"]),
                "loanIntent": str(raw["loanIntent"]),
            },
        })
    for v in vectors:
        row = design_matrix(pd.DataFrame([v["input"]]).astype({"age": float, "employmentYears": float,
                                                               "creditHistoryYears": float}), clip).iloc[0]
        v["expectedProbability"] = score_exported(model, row)
    model["testVectors"] = vectors

    MODEL_OUT.parent.mkdir(parents=True, exist_ok=True)
    MODEL_OUT.write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")
    METRICS_OUT.write_text(json.dumps(metrics, indent=2) + "\n", encoding="utf-8")
    write_plots(y_test, p_test, p_gbm, x_test["loan_to_income"].to_numpy(), metrics)

    print(json.dumps(metrics, indent=2))
    print(f"\nModel written to {MODEL_OUT.relative_to(ROOT.parent)}")
    return 0


# --------------------------------------------------------------------------- charts

def write_plots(y, p_lr, p_gbm, lti, metrics) -> None:
    import matplotlib
    matplotlib.use("Agg")
    import matplotlib.pyplot as plt

    PLOTS_DIR.mkdir(parents=True, exist_ok=True)
    ink, muted, grid = "#0f172a", "#64748b", "#e2e8f0"
    plt.rcParams.update({
        "font.size": 10, "axes.edgecolor": "#cbd5e1", "axes.labelcolor": ink, "xtick.color": muted,
        "ytick.color": muted, "axes.spines.top": False, "axes.spines.right": False,
    })

    # ROC curve
    fig, ax = plt.subplots(figsize=(5.2, 4.6), dpi=160)
    for scores, label, color, dash in [
        (p_lr, f"Logistic regression (AUC {metrics['logisticRegression']['testAUC']:.3f})", "#2554e0", "-"),
        (p_gbm, f"Gradient boosting (AUC {metrics['comparison']['gradientBoostingAUC']:.3f})", "#ec835a", "-"),
        (lti, f"Loan-to-income only (AUC {metrics['comparison']['baselineLoanToIncomeOnlyAUC']:.3f})", muted, "--"),
    ]:
        fpr, tpr, _ = roc_curve(y, scores)
        ax.plot(fpr, tpr, dash, color=color, linewidth=2, label=label)
    ax.plot([0, 1], [0, 1], ":", color="#94a3b8", linewidth=1)
    ax.set(xlabel="False positive rate", ylabel="True positive rate", xlim=(0, 1), ylim=(0, 1.01))
    ax.set_title("ROC curve (held-out test set)", loc="left", color=ink, fontsize=11, fontweight="bold")
    ax.grid(color=grid, linewidth=0.8)
    ax.legend(loc="lower right", frameon=False, fontsize=8.5)
    fig.tight_layout()
    fig.savefig(PLOTS_DIR / "roc_curve.png")
    plt.close(fig)

    # Calibration curve
    frac_pos, mean_pred = calibration_curve(y, p_lr, n_bins=10, strategy="quantile")
    fig, ax = plt.subplots(figsize=(5.2, 4.6), dpi=160)
    ax.plot([0, 1], [0, 1], ":", color="#94a3b8", linewidth=1, label="Perfect calibration")
    ax.plot(mean_pred, frac_pos, "-o", color="#2554e0", linewidth=2, markersize=5, label="Logistic regression")
    ax.set(xlabel="Predicted probability of default", ylabel="Observed default rate", xlim=(0, 1), ylim=(0, 1))
    ax.set_title("Calibration (10 quantile bins)", loc="left", color=ink, fontsize=11, fontweight="bold")
    ax.grid(color=grid, linewidth=0.8)
    ax.legend(loc="upper left", frameon=False, fontsize=8.5)
    fig.tight_layout()
    fig.savefig(PLOTS_DIR / "calibration.png")
    plt.close(fig)


if __name__ == "__main__":
    sys.exit(main())
