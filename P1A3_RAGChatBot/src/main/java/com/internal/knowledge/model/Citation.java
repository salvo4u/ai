package com.internal.knowledge.model;

import java.time.LocalDateTime;
import java.util.List;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Citation {
    private int id;
    private String sourceUrl;
    private String sourceTitle;
    private String sourceSection;
    private String previewText;
    private int contextPositionStart;
    private int contextPositionEnd;
    private List<String> authors;
    private LocalDateTime publicationDate;
}
