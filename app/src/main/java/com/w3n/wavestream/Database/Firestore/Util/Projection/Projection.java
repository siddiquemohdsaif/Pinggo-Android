package com.w3n.wavestream.Database.Firestore.Util.Projection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public interface Projection {
    Map<String, Integer> getProjection();

    static EXCLUDE EXCLUDE(String... fields){
        ArrayList<String> list = new ArrayList<>(Arrays.asList(fields));
        return new EXCLUDE(list);
    }

    static EXCLUDE EXCLUDE(ArrayList<String> fields){
        return new EXCLUDE(fields);
    }

    static INCLUDE INCLUDE(String... fields){
        ArrayList<String> list = new ArrayList<>(Arrays.asList(fields));
        return new INCLUDE(list);
    }

    static INCLUDE INCLUDE(ArrayList<String> fields){
        return new INCLUDE(fields);
    }


    class EXCLUDE implements Projection {
        private ArrayList<String> list;

        private EXCLUDE(ArrayList<String> list) {
            this.list = list;
        }


        @Override
        public Map<String, Integer> getProjection() {
            Map<String, Integer> projection = new HashMap<>();
            projection.put("_id", 0);
            for (String field: list) {
                projection.put(field, 0);
            }
            return projection;
        }
    }

    class INCLUDE implements Projection {
        private ArrayList<String> list;

        private INCLUDE(ArrayList<String> list) {
            this.list = list;
        }


        @Override
        public Map<String, Integer> getProjection() {
            Map<String, Integer> projection = new HashMap<>();
            projection.put("_id",0);
            for (String field: list) {
                projection.put(field,1);
            }
            return projection;
        }
    }
}
