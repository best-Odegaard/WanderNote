package com.gkv.service.slot;

import com.gkv.vo.SlotOptionVO;
import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 一个槽位的问题定义（由 question-tree.yml 加载）
 */
@Data
public class SlotDef {

    /** 槽位标识 */
    private String key;

    /** 完整度权重；0 表示不参与完整度计算 */
    private int weight;

    /** 提问优先级，越大越先问（与 weight 解耦，便于单独调整追问顺序） */
    private int priority;

    /** 问题文案 */
    private String question;

    /** 可选的一句话建议 */
    private String hint;

    /** 静态选项 */
    private List<SlotOptionVO> options = new ArrayList<>();

    /** 是否用热门城市列表作为选项 */
    private boolean useHotCities;

    /** 是否可跳过 */
    private boolean skippable = true;

    /** 跳过按钮文案 */
    private String skipLabel = "跳过";

    /** 跳过时填入的默认值；为空则跳过即留空 */
    private String defaultValue;

    /** 是否主动提问；false 表示只承载推断结果（如 people） */
    private boolean askable = true;

    /** 依赖条件：仅当这些槽位取到给定值之一时才会提出该问题 */
    private Map<String, List<String>> dependsOn = new LinkedHashMap<>();
}
