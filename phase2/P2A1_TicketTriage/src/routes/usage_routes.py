from fastapi import APIRouter
import csv
import datetime
from collections import defaultdict

CSV_FILE = "customer_usage.csv"

router = APIRouter(prefix="/api/usage", tags=["usage"])

@router.get("/")
async def get_usage():
    usage_summary = defaultdict(lambda: defaultdict(lambda: {"tokens": 0, "cost_usd": 0.0}))

    try:
        with open(CSV_FILE, mode="r") as f:
            reader = csv.DictReader(f)
            for row in reader:
                date = row["date"]
                customer = row["customer_id"]
                tokens = int(row["tokens_used"])
                cost = float(row["cost_usd"])

                usage_summary[date][customer]["tokens"] += tokens
                usage_summary[date][customer]["cost_usd"] += cost
    except FileNotFoundError:
        return {"error": "No usage data found"}

    # Convert defaultdicts to plain dicts for JSON response
    result = {date: dict(customers) for date, customers in usage_summary.items()}
    return result
