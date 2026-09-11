from pydantic import BaseModel
class TravelRequest(BaseModel):
    stage:str
    session_id:str
    user_input:str
    base_info:dict

if __name__=="__main__":
    test_json = {
        "stage":"chat",
        "session_id":"u1",
        "user_input":"你好",
        "base_info":{"city":"肇庆",}
    }
    res = TravelRequest(**test_json) #**字典解包操作
    print(res.stage)
    print(res.session_id)