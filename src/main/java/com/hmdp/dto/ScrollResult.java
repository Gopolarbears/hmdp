package com.hmdp.dto;

import lombok.Data;

import java.util.List;

/**
 * ClassName: ScrollResult
 * Description:
 *
 * @Author Seth Neiman
 * @Create 2024/5/22 13:43
 * @Version 1.0
 */
@Data
public class ScrollResult {
    private List<?> list;
    private Long minTime;
    private Integer offset;
}
