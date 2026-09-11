package com.gkv.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class TravelJournalPageDTO extends PageQuery {
    private String keyword;
    private String tag;
}
