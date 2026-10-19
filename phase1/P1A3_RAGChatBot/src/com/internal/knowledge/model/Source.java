package com.internal.knowledge.model;

import java.time.LocalDateTime;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Source {
    private int id;
    private String chunkId;
    private String title;
    private String source;
    private String section;
    private String url;
    private String citationId;
    private LocalDateTime lastUpdated;
}
