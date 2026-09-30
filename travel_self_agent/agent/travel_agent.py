
import json
import os
from langchain_core.runnables import RunnableLambda
from langchain_openai import  ChatOpenAI
from langchain_core.prompts import PromptTemplate
from langchain_core.messages import HumanMessage, AIMessage, SystemMessage
from utils.prompt_loader import load_chat_prompt
from utils.prompt_loader import load_plan_prompt, load_plan_frame_prompt, load_plan_detail_prompt
from utils.config_handler import agent_config
from model.structured_model import TripPlan, TripPlanFrame
from typing import Generator

# API Key 仅从环境变量读取，请勿硬编码进代码库
# 启动前设置：export MAAS_API_KEY=sk-your-own-key（Windows 用 set / $env:）
MAAS_API_KEY = os.environ.get("MAAS_API_KEY", "").strip()
if not MAAS_API_KEY:
    raise RuntimeError(
        "未检测到环境变量 MAAS_API_KEY。\n"
        "请先设置大模型 API Key 后再启动服务，例如：\n"
        "  Linux / macOS : export MAAS_API_KEY=sk-xxxxxxxx\n"
        "  Windows CMD   : set MAAS_API_KEY=sk-xxxxxxxx\n"
        "  PowerShell    : $env:MAAS_API_KEY=\"sk-xxxxxxxx\""
    )

# 模型名一律从 config/agent.yml 读，不在这里硬编码。
#
# 原来两个模型名是写死的，而 config/agent.yml 里的同名配置只被打印过、从未参与构造 ——
# 结果「改配置换模型」完全不生效，排查时容易怀疑到别的地方去。
# 现在统一成一处配置：换模型只改 config/agent.yml。
CHAT_MODEL = agent_config.get("chat_model") or "qwen3.8-flash"
PLAN_MODEL = agent_config.get("plan_model") or "deepseek-v4-pro-0813"

# 所有模型共用同一个 OpenAI 兼容端点与同一把 Key
MAAS_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"

class TravelAgent:
    def __init__(self):
        # 对话模型：小笺的多轮短回复，量大、要求快
        self.llm_chat = ChatOpenAI(
            base_url=MAAS_BASE_URL,
            api_key=MAAS_API_KEY,
            model=CHAT_MODEL,
            temperature=0.7
        )

        # 行程生成模型：要按 Pydantic 模型吐结构化 JSON，对指令遵循更敏感
        self.llm_plan = ChatOpenAI(
            base_url=MAAS_BASE_URL,
            api_key=MAAS_API_KEY,
            model=PLAN_MODEL,
            temperature=0.3
        )
        self.plan_prompt = PromptTemplate(
            input_variables=["base_info","scenic_data", "note_data"], #接受的必须是字典
            template=load_plan_prompt()
        )
        # 阶段一：行程框架（轻量、快速，先给用户看骨架）
        self.plan_frame_prompt = PromptTemplate(
            input_variables=["base_info","scenic_data", "note_data"],
            template=load_plan_frame_prompt()
        )
        # 阶段二：完整行程详情（在原框架基础上细化）
        self.plan_detail_prompt = PromptTemplate(
            input_variables=["base_info","scenic_data", "note_data", "frame"],
            template=load_plan_detail_prompt()
        )
        self.chat_prompt = PromptTemplate(
            input_variables=["base_info"],
            template=load_chat_prompt()
        )
        self.chat_chain=(RunnableLambda(self.build_chat_messages)|self.llm_chat)
        self.plan_chain=(RunnableLambda(self.build_plan_messages)|self.llm_plan.with_structured_output(TripPlan))
        self.plan_frame_chain=(RunnableLambda(self.build_plan_frame_messages)|self.llm_plan.with_structured_output(TripPlanFrame))
        self.plan_detail_chain=(RunnableLambda(self.build_plan_detail_messages)|self.llm_plan.with_structured_output(TripPlan))
#这一块都是链式里面的组装函数
    @staticmethod  #规范的写法表示这是个工具函数，不需要self
    def to_langchain_messages(history:list)->list:
        messages=[]
        for msg in history:
            if msg.role == "user":
                messages.append(HumanMessage(content=msg.content))
            elif msg.role == "assistant":
                messages.append(AIMessage(content=msg.content))
        return messages

    def build_plan_messages(self,inputs:dict)-> list:
        system_text =self.plan_prompt.format(
            base_info=inputs["base_info"],
            scenic_data=inputs["scenic_data"],
            note_data=inputs["note_data"]
        )
        history_messages=self.to_langchain_messages(inputs.get("chat_history",[]))
        user_input=inputs["user_input"] or "生成最终行程"
        return[
            SystemMessage(content=system_text),
            *history_messages, #*是展开的意思
            HumanMessage(content=user_input),
        ]

    def build_plan_frame_messages(self,inputs:dict)-> list:
        """阶段一：行程框架（每天只列景点名单，输出小、快）"""
        system_text = self.plan_frame_prompt.format(
            base_info=inputs["base_info"],
            scenic_data=inputs["scenic_data"],
            note_data=inputs["note_data"]
        )
        history_messages=self.to_langchain_messages(inputs.get("chat_history",[]))
        user_input=inputs["user_input"] or "生成行程框架"
        return[
            SystemMessage(content=system_text),
            *history_messages,
            HumanMessage(content=user_input),
        ]

    def build_plan_detail_messages(self,inputs:dict)-> list:
        """阶段二：在已确认框架上生成完整详情"""
        system_text = self.plan_detail_prompt.format(
            base_info=inputs["base_info"],
            scenic_data=inputs["scenic_data"],
            note_data=inputs["note_data"],
            frame=json.dumps(inputs.get("frame", {}), ensure_ascii=False)
        )
        history_messages=self.to_langchain_messages(inputs.get("chat_history",[]))
        user_input=inputs["user_input"] or "生成最终行程"
        return[
            SystemMessage(content=system_text),
            *history_messages,
            HumanMessage(content=user_input),
        ]

    def build_chat_messages(self,inputs)-> list:
        # 每轮重新读一次对话提示词文件，而不是用启动时缓存的那份。
        #
        # 原因：缓存会导致「改了 prompt 必须重启进程才生效」，这个摩擦很坑人 ——
        # 人改完文案看到毫无变化，只会去别处找原因（实测为此浪费过好几轮）。
        # 读的是几 KB 的纯文本，开销可以忽略。
        system_text=PromptTemplate(
            input_variables=["base_info"],
            template=load_chat_prompt()
        ).format(
            base_info=inputs["base_info"]
        )
        history_message=self.to_langchain_messages(inputs.get("chat_history",[]))
        user_input=inputs["user_input"]
        return[
            SystemMessage(content=system_text),
            *history_message,
            HumanMessage(content=user_input)
        ]
#这是链式输入前和输出后的需求
    #把python对象处理成了字符串文本
    def chat_stage(self,base_info: str,user_input:str,chat_history:list) ->Generator[str, None, None]:
        full_message = None
        for chunk in self.chat_chain.stream({
            "base_info": base_info,
            "chat_history": chat_history,
            "user_input": user_input,
        }):
            full_message=chunk if full_message is None else full_message+chunk
            text=chunk.content
            if text:
                yield text

    def plan_stage(self,base_info:str,user_input:str,scenic_data: str,
            note_data: str,chat_history: list):
        result:TripPlan=self.plan_chain.invoke({
            "base_info": base_info,
            "chat_history": chat_history,
            "user_input": user_input,
            "scenic_data": scenic_data,
            "note_data": note_data,
        })
        return result.model_dump() #把 Pydantic 对象转换成一个 Python 字典

    def plan_frame_stage(self,base_info:str,user_input:str,scenic_data: str,
            note_data: str,chat_history: list):
        """阶段一：生成行程框架（轻量输出，速度快），返回 dict"""
        result:TripPlanFrame=self.plan_frame_chain.invoke({
            "base_info": base_info,
            "chat_history": chat_history,
            "user_input": user_input,
            "scenic_data": scenic_data,
            "note_data": note_data,
        })
        return result.model_dump()

    def plan_detail_stage(self,base_info:str,user_input:str,scenic_data: str,
            note_data: str,chat_history: list,frame:dict|None=None):
        """阶段二：基于已确认框架生成完整行程详情，返回 dict"""
        result:TripPlan=self.plan_detail_chain.invoke({
            "base_info": base_info,
            "chat_history": chat_history,
            "user_input": user_input,
            "scenic_data": scenic_data,
            "note_data": note_data,
            "frame": frame or {},
        })
        return result.model_dump()
