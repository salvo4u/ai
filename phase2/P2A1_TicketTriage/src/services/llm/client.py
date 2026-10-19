import openai

class LLMClient:
    def __init__(self):
        self.client = openai.OpenAI()

    def stream_completion(self, messages, model="gpt-4o-mini"):
        with self.client.chat.completions.stream(model=model, messages=messages) as stream:
            for event in stream:
                if event.type == "token":
                    yield event.token
                elif event.type == "error":
                    yield f"\nError: {event.error}"
                elif event.type == "completed":
                    yield "\n--- Stream completed ---"

