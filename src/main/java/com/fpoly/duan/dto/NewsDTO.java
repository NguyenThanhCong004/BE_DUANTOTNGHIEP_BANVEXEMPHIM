package com.fpoly.duan.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewsDTO {
    private Integer id;
    private String title;
    private String content;
    private String image;
    /** 1 = public, 0 = draft */
    private Integer status;
    private LocalDateTime createdAt;
}
