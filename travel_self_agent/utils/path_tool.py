'''
将路径统一处理
'''



import os
#父级目录，绝对路径
def get_project_root()-> str:
    current_path = os.path.abspath(__file__)
    current_dirc = os.path.dirname(current_path)
    return os.path.dirname(current_dirc)

def get_abs_path(relative:str)->str:
    return os.path.join(get_project_root(),relative)

# print(get_abs_path("utils\path_tool"))