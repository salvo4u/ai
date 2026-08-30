package com.internal.knowledge.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FAQDocument {
    private String id;
    private String question;
    private String answer;
    private String source;
    private List<String> keywords;
    private LocalDateTime createdAt;
}
