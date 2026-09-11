'''
在大模型输出文本前让大模型以固定的对象输出，方便转换为json格式传个后端
用
'''

from pydantic import BaseModel,Field
from typing import List
class Attraction(BaseModel):
    #单个景点
    visit_time_range: str=Field(description="游玩时间段，如 09:00-11:00")
    spot_name:str=Field(description="游玩景点名称")
    open_time:str=Field(description="景点的开放时间，如09:00-11:00，无数据时填现场咨询")
    ticket:str=Field(description="景区的门票,如免费，78元，无数据时填现场咨询")
    location:str=Field(description="景点的具体地址")
    feature_tag: str = Field(
        description="景点特色标签，必须从通用特色标签库中选择一个最匹配的，如：自然风光、拍照出片、地方老字号、河鲜/海鲜等")
class Day(BaseModel):
    date:str=Field(description="日期,如Day1")
    schedule:List[Attraction]=Field(description="当天的景点列表")
    total_spot:int=Field(description="当天的总景点数")
    total_distance:float=Field(description="当天的总公里数")

class TripPlan(BaseModel):
    title:str= Field(description="行程标题，如肇庆三日游")
    day_list:List[Day]=Field(description="按天安排的行程")
    total_walk:str=Field(description="预估步行时长")


# ── 行程框架（第一阶段的轻量输出，用于秒出骨架预览） ──
class FrameDay(BaseModel):
    """框架阶段：每天只列出景点名单，不填详情，输出小、速度快"""
    date:str=Field(description="日期,如Day1")
    spot_names:List[str]=Field(description="当天计划游玩的景点名单，3-6个")

class TripPlanFrame(BaseModel):
    title:str=Field(description="行程标题，如肇庆三日游")
    day_list:List[FrameDay]=Field(description="按天安排的行程框架")
    total_walk:str=Field(description="预估步行时长")

