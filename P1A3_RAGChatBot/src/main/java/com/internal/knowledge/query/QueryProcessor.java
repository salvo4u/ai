package com.internal.knowledge.query;

import com.internal.knowledge.model.Query;
import com.internal.knowledge.util.PIIRedactionUtil;

import java.util.ArrayList;
import java.util.HashMap;

public class QueryProcessor {
    private final PIIRedactionUtil piiUtil;

    public QueryProcessor() {
        this.piiUtil = new PIIRedactionUtil();
    }

    public Query process(Query query) {
        String redactedText = piiUtil.redactPII(query.getText());
        boolean hasPII = piiUtil.containsPII(query.getText());

        Query.QueryMetadata metadata = Query.QueryMetadata.builder()
            .containsPII(hasPII)
            .redactedText(redactedText)
            .intent("GENERAL_QUERY")
            .urgencyScore(0.5)
            .entities(new ArrayList<>())
            .context(new HashMap<>())
            .build();

        query.setMetadata(metadata);
        query.setText(redactedText);
        return query;
    }
}
