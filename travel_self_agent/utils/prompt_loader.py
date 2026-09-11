'''
读取所有提示词文件里的内容
'''
from utils.config_handler import prompt_config
from utils.path_tool import get_abs_path
from utils.logger_handler import logger

def load_chat_prompt():
    try:
        chat_prompt=get_abs_path(prompt_config["chat_prompt_path"])
    except KeyError as e:
        logger.error(f"[load_chat_prompts]在yaml配置中没有chat_path的配置")
        raise e

    try:
        return open(chat_prompt,"r",encoding="utf-8").read()
    except Exception as e:
        logger.error(f"无法正确解析chat_path的提示词,{str(e)}")

def load_plan_prompt():
    try:
        plan_prompt=get_abs_path(prompt_config["plan_prompt_path"])
    except KeyError as e:
        logger.error(f"[load_plan_prompt]在yanl配置中没有plan_prompt的配置")

    try:
        return open(plan_prompt,"r",encoding="utf-8").read()
    except Exception as e:
        logger.error(f"无法正确解析plan_prompt的提示词，{str(e)}")


def load_plan_frame_prompt():
    try:
        plan_prompt=get_abs_path(prompt_config["plan_frame_prompt_path"])
    except KeyError as e:
        logger.error(f"[load_plan_frame_prompt]在yaml配置中没有plan_frame_prompt的配置")

    try:
        return open(plan_prompt,"r",encoding="utf-8").read()
    except Exception as e:
        logger.error(f"无法正确解析plan_frame_prompt的提示词，{str(e)}")


def load_plan_detail_prompt():
    try:
        plan_prompt=get_abs_path(prompt_config["plan_detail_prompt_path"])
    except KeyError as e:
        logger.error(f"[load_plan_detail_prompt]在yaml配置中没有plan_detail_prompt的配置")

    try:
        return open(plan_prompt,"r",encoding="utf-8").read()
    except Exception as e:
        logger.error(f"无法正确解析plan_detail_prompt的提示词，{str(e)}")


if __name__ == "__main__":
     print(load_chat_prompt())

     print("-----------------------------------")
     print(load_plan_prompt())
