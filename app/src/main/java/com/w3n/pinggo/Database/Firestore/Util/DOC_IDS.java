package com.w3n.pinggo.Database.Firestore.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class DOC_IDS {

    private List<String> ids;

    private DOC_IDS(List<String> ids) {
        this.ids = ids;
    }

    public static DOC_IDS LIST(String... docIds) {
        return new DOC_IDS(new ArrayList<>(Arrays.asList(docIds)));
    }

    public static DOC_IDS LIST(List<String> ids) {
        return new DOC_IDS(ids);
    }

    public List<String> getIds() {
        return ids;
    }
}
