package com.tianji.api.dto.order;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OrderBasicDTO {
    private Long orderId;
    private Long userId;
    private List<Long> courseIds;
}
