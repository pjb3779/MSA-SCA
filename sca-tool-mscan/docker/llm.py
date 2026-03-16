import os

# OpenRouter(OpenAI-compatible)
# - base_url: https://openrouter.ai/api/v1
# - model: deepseek/deepseek-r1
#
# NOTE(dev): 개발 단계에서는 application.yml에 키를 넣어도 되지만,
# NOTE(ops): 운영에서는 Secret YAML/Secret Store로 교체 권장.

OPENAI_API_KEY = os.getenv("OPENAI_API_KEY", "")
OPENAI_BASE_URL = os.getenv("OPENAI_BASE_URL", "https://openrouter.ai/api/v1")
OPENAI_MODEL = os.getenv("OPENAI_MODEL", "deepseek/deepseek-r1")