package com.vinland.app.ai.direct;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** JSONObject -> plain Map/List, the shape AiRecommendationResponse.fromMap reads. */
final class JsonMaps {

    private JsonMaps() {
    }

    static Map<String, Object> toMap(JSONObject o) throws JSONException {
        Map<String, Object> m = new HashMap<>();
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
            String k = keys.next();
            m.put(k, toPlain(o.get(k)));
        }
        return m;
    }

    private static Object toPlain(Object v) throws JSONException {
        if (v == JSONObject.NULL) return null;
        if (v instanceof JSONObject) return toMap((JSONObject) v);
        if (v instanceof JSONArray) {
            JSONArray a = (JSONArray) v;
            List<Object> list = new ArrayList<>();
            for (int i = 0; i < a.length(); i++) list.add(toPlain(a.get(i)));
            return list;
        }
        return v;
    }
}
