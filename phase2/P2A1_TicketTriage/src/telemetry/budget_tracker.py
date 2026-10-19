import csv
import datetime
import os

CSV_FILE = "customer_usage.csv"
DAILY_BUDGET_USD = 10.0
MAX_TOKENS_PER_REQUEST = 2000

def init_csv():
    """Ensure CSV file exists with headers."""
    if not os.path.exists(CSV_FILE):
        with open(CSV_FILE, mode="w", newline="") as f:
            writer = csv.writer(f)
            writer.writerow(["date", "customer_id", "ticket_id", "tokens_used", "cost_usd"])

def log_usage(customer_id: str, ticket_id: str, tokens_used: int, cost_usd: float):
    """Append usage record to CSV."""
    init_csv()
    today = datetime.date.today().isoformat()
    with open(CSV_FILE, mode="a", newline="") as f:
        writer = csv.writer(f)
        writer.writerow([today, customer_id, ticket_id, tokens_used, round(cost_usd, 6)])

def get_daily_usage(customer_id: str) -> dict:
    """Sum tokens and cost for a customer today."""
    init_csv()
    today = datetime.date.today().isoformat()
    total_tokens, total_cost = 0, 0.0
    with open(CSV_FILE, mode="r") as f:
        reader = csv.DictReader(f)
        for row in reader:
            if row["date"] == today and row["customer_id"] == customer_id:
                total_tokens += int(row["tokens_used"])
                total_cost += float(row["cost_usd"])
    return {"tokens": total_tokens, "cost_usd": total_cost}

def enforce_request_limit(tokens: int):
    """Raise error if request exceeds token ceiling."""
    if tokens > MAX_TOKENS_PER_REQUEST:
        raise ValueError(f"Request exceeds token ceiling ({tokens} > {MAX_TOKENS_PER_REQUEST})")

def enforce_daily_budget(customer_id: str, new_cost: float):
    """Raise error if daily budget exceeded."""
    usage = get_daily_usage(customer_id)
    if usage["cost_usd"] + new_cost > DAILY_BUDGET_USD:
        raise ValueError(
            f"Daily budget exceeded for {customer_id}: "
            f"{usage['cost_usd'] + new_cost} > {DAILY_BUDGET_USD}"
        )
