
'''
读取各自的yml文件内容
'''


import yaml
from utils.path_tool import get_abs_path
def load_rag_config(path:str=get_abs_path("config/rag.yml"),encoding:str="utf-8"):
    with open(path,"r",encoding=encoding) as f:
        return yaml.load(f,Loader=yaml.FullLoader)

def load_chroma_config(path:str=get_abs_path("config/chroma.yml"),encoding:str="utf-8"):
    with open(path,"r",encoding=encoding) as f:
        return yaml.load(f,Loader=yaml.FullLoader)

def load_agent_config(path:str=get_abs_path("config/agent.yml"),encoding:str="utf-8"):
    with open(path,"r",encoding=encoding) as f:
        return yaml.load(f,Loader=yaml.FullLoader)

def load_prompt_config(path:str=get_abs_path("config/prompt.yml"),encoding:str="utf-8"):
    with open(path,"r",encoding=encoding) as f:
        return yaml.load(f,Loader=yaml.FullLoader)

prompt_config = load_prompt_config()
chroma_config = load_chroma_config()
rag_config = load_rag_config()
agent_config=load_agent_config()
# print(prompt_config)
print(chroma_config)
print(rag_config)
print(agent_config)