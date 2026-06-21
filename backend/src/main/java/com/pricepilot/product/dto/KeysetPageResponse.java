package com.pricepilot.product.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeysetPageResponse<T> {
    private List<T> content;
    private String nextCursor;
    private String prevCursor;
    private boolean hasMore;
}
