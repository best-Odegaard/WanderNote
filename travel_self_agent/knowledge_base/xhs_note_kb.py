class XhsNoteKB:
    """小红书攻略知识库（按城市索引）。

    目前仅收录肇庆的演示数据；后续可替换为真实数据库查询。
    未收录的城市返回空字符串，由上层提示词让大模型基于常识补充。
    """

    # 城市 -> 攻略文本
    CITY_NOTES = {
        "肇庆": "推荐晚上去岩前村吃河鲜，肇庆裹蒸粽很有名。"
    }

    @staticmethod
    def search_note(city: str, tags: list) -> str:
        city = (city or "").strip().rstrip("市")
        return XhsNoteKB.CITY_NOTES.get(city, "")
