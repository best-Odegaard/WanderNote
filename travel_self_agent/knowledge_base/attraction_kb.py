class AttractionKB:
    """本地景点知识库（按城市索引）。

    目前仅收录肇庆的演示数据；后续可替换为真实数据库查询。
    query_by_city_tag 必须按城市精确匹配，未收录的城市返回空列表，
    由上层根据提示词让大模型基于目的地常识规划行程。
    """

    # 城市 -> 景点列表（key 使用城市名，查询时会做"市"后缀归一化）
    CITY_SPOTS = {
        "肇庆": [
            {"name": "七星岩", "open_time": "08:00-17:30", "ticket": "78元", "tag": "自然风光", "location": "肇庆市端州区"},
            {"name": "鼎湖山", "open_time": "08:00-18:00", "ticket": "60元", "tag": "自然风光", "location": "肇庆市鼎湖区"}
        ]
    }

    @staticmethod
    def query_by_city_tag(city: str, tags: list) -> list:
        # 归一化：去掉"市"后缀后匹配（如 "肇庆市" -> "肇庆"）
        city = (city or "").strip().rstrip("市")
        spots = AttractionKB.CITY_SPOTS.get(city, [])
        if not spots:
            return []
        # 有库数据时按偏好标签简单过滤：标签未命中任何景点 tag 时返回全部，避免空数据
        if tags:
            matched = [s for s in spots if s.get("tag") in tags]
            if matched:
                return matched
        return spots
