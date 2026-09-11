'''
读取大模型返回的数据读取其中的json格式
'''


import json
import re
#从LLm返回的啰嗦文本中，提取出纯粹的JSON对象
def extract_and_clean_json(raw_json)->dict|None:
    if raw_json is None:
        return None
    #先直接解析，不清理
    try:
        return json.loads(raw_json)
    except Exception:
        pass
    #对数据清理后拿到json格式
    #re.search(匹配要求，匹配对象，【匹配的模式】)
    match = re.search(r'```json\s*(.*?)\s*```',raw_json,re.DOTALL)
    if match:
        #如果找到了，就把代码块里面的内容拿出来
        json_str=match.group(1).strip()
    else:
        #如果没有找到就用{}之间的内容,对内容进行切片
        start=raw_json.find('{') #从左往右找
        end=raw_json.rfind('}') #从右往找
        if start!=-1 and end!=-1 and end>start:
            json_str=raw_json[start:end+1]
        else:
            return None
    #对json格式进行清洗
    json_str=json_str.replace("，",",")
    json_str=json_str.replace('“','"').replace('”','"')
    try:
        return json.loads(json_str)
    except Exception:
        return None

if __name__ == "__main__":
    # 测试1：带Markdown代码块和废话
    test1 = '好的，这是您的行程：```json\n{"title": "肇庆游", "days": 2}\n```祝您愉快！'
    result1 = extract_and_clean_json(test1)
    print("测试1结果:", result1)  # 期望：{'title': '肇庆游', 'days': 2}

    # 测试2：没有代码块，JSON混在文字中
    test2 = '这是行程 {"name": "七星岩", "ticket": 78}，请查收。'
    result2 = extract_and_clean_json(test2)
    print("测试2结果:", result2)  # 期望：{'name': '七星岩', 'ticket': 78}

    # 测试3：中文逗号污染
    test3 = '{"城市":"肇庆"，"景点":["七星岩"，"鼎湖山"]}'
    result3 = extract_and_clean_json(test3)
    print("测试3结果:", result3)  # 期望：修复后成功解析

    # 测试4：无JSON
    test4 = "你好，今天天气不错。"
    result4 = extract_and_clean_json(test4)
    print("测试4结果:", result4)  # 期望：None