package com.gkv.vo;

import lombok.Data;

import java.util.List;

/**
 * Excel 批量导入结果
 */
@Data
public class ImportResultVO {
    /** 总行数（不含表头，跳过空行） */
    private Integer total;
    /** 成功条数 */
    private Integer success;
    /** 失败条数 */
    private Integer fail;
    /** 失败明细（行号 + 原因） */
    private List<ImportErrorVO> errors;
}
