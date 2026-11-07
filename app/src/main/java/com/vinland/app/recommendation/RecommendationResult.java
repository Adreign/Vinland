package com.vinland.app.recommendation;

import com.vinland.app.data.Crop;

import java.util.ArrayList;
import java.util.List;

/** Validated, structured output of the LLM recommendation engine. */
public class RecommendationResult {

    public static class CropPick {
        public Crop crop;          // always a crop that exists in the database
        public int suitability;    // 0-100
        public String reason = "";
        public String adjustments = "";
    }

    public final List<CropPick> crops = new ArrayList<>();
    public final List<String> amendments = new ArrayList<>();
    public String soilStatus = "";
    public String summary = "";
    public String topAction = "";
    public String watering = "";
    public String report = "";
    /** Which backend produced this result, e.g. "Groq · llama-3.3-70b-versatile". */
    public String provider = "";
}
