import time

PRICING = {
    "gpt-4o-mini": {"prompt": 0.000001, "completion": 0.000002},
    "gpt-4o": {"prompt": 0.000003, "completion": 0.000006}
}

def start_timer() -> float:
    return time.time()

def end_timer(start: float) -> int:
    return int((time.time() - start) * 1000)

def calculate_cost(model: str, prompt_tokens: int, completion_tokens: int) -> float:
    pricing = PRICING.get(model, {"prompt": 0, "completion": 0})
    return round(
        (prompt_tokens * pricing["prompt"]) + (completion_tokens * pricing["completion"]),
        6
    )

def build_telemetry(model: str, usage, latency_ms: int) -> dict:
    return {
        "model": model,
        "latency_ms": latency_ms,
        "prompt_tokens": usage.prompt_tokens,
        "completion_tokens": usage.completion_tokens,
        "total_tokens": usage.total_tokens,
        "cost_usd": calculate_cost(model, usage.prompt_tokens, usage.completion_tokens)
    }

def aggregate_telemetry(attempts: list[dict]) -> dict:
    return {
        "total_latency_ms": sum(a["latency_ms"] for a in attempts),
        "total_prompt_tokens": sum(a["prompt_tokens"] for a in attempts),
        "total_completion_tokens": sum(a["completion_tokens"] for a in attempts),
        "total_tokens": sum(a["total_tokens"] for a in attempts),
        "total_cost_usd": round(sum(a["cost_usd"] for a in attempts), 6),
        "attempts": attempts
    }