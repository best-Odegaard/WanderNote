import asyncio
import json
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from pydantic import BaseModel,Field
from agent.travel_agent import TravelAgent
from knowledge_base.attraction_kb import AttractionKB
from knowledge_base.xhs_note_kb import XhsNoteKB
from typing import Optional


app=FastAPI(title="旅游服务智能体")
agent=TravelAgent()

class BaseInfo(BaseModel):
    """行程基础信息。

    所有字段都是可选的：改造后用户从「一句话」直接进对话，
    首轮可能只说了目的地，甚至什么都没说。
    如果这里保持必填，Pydantic 会直接返回 422，用户看到的是"请求失败"而不是 AI 的追问。
    缺省值统一为 None / 空，由 prompt 引导模型主动补问。
    """

    departure_city: Optional[str] = Field(default=None, description="用户出发的城市，例如 '广州'")
    destination_city: Optional[str] = Field(default=None, description="用户想去旅游的目的地城市，例如 '肇庆'")
    start_day: Optional[str] = Field(default=None, description="出发日期，例如 '2026-07-01'")
    end_date: Optional[str] = Field(default=None, description="返回日期，例如 '2026-07-03'")
    days: Optional[int] = Field(default=None, description="旅游总天数")
    hobby: list[str] = Field(default_factory=list, description="旅游偏好标签，如 ['自然风光', '美食探店']")
    people_num: Optional[str] = Field(default=None, description="出行人数，例如 '两人' 或 '一家三口'")
    budget: Optional[str] = Field(default=None, description="预算范围，例如 '2000' 或 '人均1000'")
    # 用户历史画像回灌文本（由 Java 后端服务端注入，前端不传）。
    # 必须在这里显式声明：pydantic 默认 extra='ignore'，未声明的字段会被静默丢弃，
    # 那样上游看着传了 profile_note，实际 prompt 里什么都没有。
    # 它随 model_dump() 一起进 base_info 的 JSON，被 {base_info} 插值进各 prompt。
    profile_note: str = Field(default="", description="用户历史画像（后端注入，可直接参考，不要重复追问已知信息）")
    # 槽位引擎收集到的结构化偏好（酒店风格 / 节奏 / 同行人等），由 Java 侧下发，
    # 一并插值进 prompt，避免模型重复追问已知信息。
    slot_note: str = Field(default="", description="已确认的结构化需求（后端槽位引擎注入，不要重复追问）")

class ChatMessage(BaseModel):
    role:str
    content:str

class ChatRequest(BaseModel):
    session_id: str
    user_input: str
    # 冷启动时前端可能还没有任何行程信息，这里必须允许缺省（缺省即空对象）
    base_info: BaseInfo = Field(default_factory=BaseInfo)
    chat_history: list[ChatMessage] = []

class PlanRequest(BaseModel):
    session_id: str
    user_input: Optional[str] = None
    base_info: BaseInfo = Field(default_factory=BaseInfo)
    chat_history: list[ChatMessage] = []
    # 阶段二参考的行程框架（由 Java 侧先调用 frame 模式取得）
    frame: Optional[dict] = None

@app.post("/api/chat")
async def chat_endpoint(req:ChatRequest):

    def generate():
        full_reply=""
        base_info_str=json.dumps(req.base_info.model_dump(),ensure_ascii=False)
        for text in agent.chat_stage(base_info_str,req.user_input,req.chat_history):
            full_reply += text
            yield text #流式输出
    return  StreamingResponse(generate(),media_type="text/plain; charset=utf-8")

@app.post("/api/plan")
async def paln_endpoint(req:PlanRequest, mode:str="full"):
    """行程生成接口。
    - mode=full  ：一次性完整生成（向后兼容）
    - mode=frame ：仅生成行程框架（轻量快速，用于先给用户看骨架）
    - mode=detail：基于 frame 生成完整详情（需在 body 中携带 frame）
    """
    base_info_str=json.dumps(req.base_info.model_dump(),ensure_ascii=False)
    city=req.base_info.destination_city
    hobby_tags=req.base_info.hobby

    spots =AttractionKB.query_by_city_tag(city,hobby_tags)
    scenic_json=json.dumps(spots,ensure_ascii=False)
    note_text = XhsNoteKB.search_note(city,hobby_tags)

    if mode == "frame":
        result = await asyncio.to_thread(
            agent.plan_frame_stage,
            base_info=base_info_str,
            chat_history=req.chat_history,
            user_input=req.user_input or "",
            scenic_data=scenic_json,
            note_data=note_text,
        )
        return {"frame": result}

    if mode == "detail":
        result = await asyncio.to_thread(
            agent.plan_detail_stage,
            base_info=base_info_str,
            chat_history=req.chat_history,
            user_input=req.user_input or "",
            scenic_data=scenic_json,
            note_data=note_text,
            frame=req.frame,
        )
        return {"plan_data": result}

    # 默认 full：直接完整生成
    # LLM 调用耗时长（数十秒），放入线程池执行，避免阻塞事件循环导致并发请求互相排队
    result = await asyncio.to_thread(
        agent.plan_stage,
        base_info=base_info_str,
        chat_history=req.chat_history,
        user_input=req.user_input or "",
        scenic_data=scenic_json,
        note_data=note_text,
    )
    return {"plan_data":result}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8002)