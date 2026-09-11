package com.gkv.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Excel 导入失败明细
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportErrorVO {
    /** Excel 行号（从 2 开始，第 1 行为表头） */
    private Integer row;
    /** 失败原因 */
    private String message;
}
