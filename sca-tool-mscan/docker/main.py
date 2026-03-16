from model import new_llm
from pydantic import BaseModel, Field
from pydantic_core import ValidationError
from langchain_core.prompts import ChatPromptTemplate, FewShotChatMessagePromptTemplate
from prompt.example import GATEWAY_EXAMPLE
from prompt.system import GATEWAY_ENTRY_SCAN_TASK
import os
import re

def _extract_first_json_object(text: str) -> str:
    """
    LLM 응답에서 JSON 객체( { ... } )만 최대한 안정적으로 추출.
    - ```json code fence 제거
    - 첫 '{' ~ 마지막 '}'만 잘라냄
    """
    if text is None:
        return ""

    t = text.strip()
    t = re.sub(r"```(?:json)?", "", t, flags=re.IGNORECASE).strip()
    t = t.replace("```", "").strip()

    i = t.find("{")
    j = t.rfind("}")
    if i < 0 or j < 0 or j <= i:
        return t
    return t[i:j+1].strip()

if __name__ == "__main__":

    example_prompt = ChatPromptTemplate.from_messages(
        [
            ("human", GATEWAY_ENTRY_SCAN_TASK),
            ("ai", "{output}")
        ]
    )

    few_shot_prompt = FewShotChatMessagePromptTemplate(
        examples=GATEWAY_EXAMPLE,
        example_prompt=example_prompt
    )

    final_prompt = ChatPromptTemplate.from_messages(
        [
            few_shot_prompt,
            ("human", GATEWAY_ENTRY_SCAN_TASK)
        ]
    )

    class GatewayConfig(BaseModel):
        external_entries: list[str] = Field(description="A list of all user-accessible (externally exposed) entry point paths.")
        internal_entries: list[str] = Field(description="A list of all internal (not externally exposed) entry point paths.")

    # structured LLM
    llm_structured = new_llm().with_structured_output(GatewayConfig)
    chain = final_prompt | llm_structured

    # fallback용 raw LLM (structured output 없이)
    llm_raw = new_llm()
    chain_raw = final_prompt | llm_raw

    for k in os.listdir("input"):
        with open("input/" + k, encoding="utf-8") as f:
            data = f.read()

        try:
            r: GatewayConfig = chain.invoke({"input": data})
        except ValidationError:
            # 1) raw로 다시 호출해서 텍스트를 얻고
            msg = chain_raw.invoke({"input": data})
            raw_text = getattr(msg, "content", str(msg))

            # 2) JSON만 추출해서 pydantic으로 검증/파싱
            cleaned = _extract_first_json_object(raw_text)
            r = GatewayConfig.model_validate_json(cleaned)

        print(r)

        with open("output/" + k.split(".")[0] + ".json", encoding="utf-8", mode="w+") as f:
            f.write(r.model_dump_json())

        print("finish: " + k)