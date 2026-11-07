package com.vinland.app.ai;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.view.ViewCompat;

import com.vinland.app.R;
import com.vinland.app.ai.model.AiRecommendation;

import java.util.List;

/** Labels, colors and card building shared by the AI screen and the Saved Recommendations screen. */
public final class AiRecommendationViews {

    private AiRecommendationViews() {
    }

    /** Adds one item_ai_recommendation card per recommendation to {@code parent}. */
    public static void addRecommendationCards(ViewGroup parent, List<AiRecommendation> recs) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        for (AiRecommendation rec : recs) {
            View item = inflater.inflate(R.layout.item_ai_recommendation, parent, false);
            TextView tvPriority = item.findViewById(R.id.tvPriority);
            tvPriority.setText(priorityLabel(rec.priority));
            tint(tvPriority, priorityColor(rec.priority));
            ((TextView) item.findViewById(R.id.tvCategory)).setText(
                    categoryLabel(rec.category) + (rec.crop != null ? " · " + rec.crop : ""));
            ((TextView) item.findViewById(R.id.tvTitle)).setText(rec.title);
            ((TextView) item.findViewById(R.id.tvReason)).setText(rec.reason);
            ((TextView) item.findViewById(R.id.tvAction)).setText(rec.suggestedAction);
            parent.addView(item);
        }
    }

    public static void tint(View v, int color) {
        ViewCompat.setBackgroundTintList(v, ColorStateList.valueOf(color));
    }

    public static String bulletList(List<String> items) {
        StringBuilder sb = new StringBuilder();
        for (String s : items) {
            if (sb.length() > 0) sb.append('\n');
            sb.append("• ").append(s);
        }
        return sb.toString();
    }

    public static String priorityLabel(String p) {
        if ("high".equals(p)) return "HIGH PRIORITY";
        if ("low".equals(p)) return "LOW PRIORITY";
        return "MEDIUM PRIORITY";
    }

    public static int priorityColor(String p) {
        if ("high".equals(p)) return 0xFFD32F2F;
        if ("low".equals(p)) return 0xFF2E7D32;
        return 0xFFF9A825;
    }

    public static String categoryLabel(String c) {
        if ("crop".equals(c)) return "🌿 Crop";
        if ("soil".equals(c)) return "🪴 Soil";
        if ("watering".equals(c)) return "💧 Watering";
        return "📌 General";
    }

    public static String soilLabel(String s) {
        if ("optimal".equals(s)) return "Optimal";
        if ("fair".equals(s)) return "Fair";
        if ("action_needed".equals(s)) return "Action needed";
        return "Unknown";
    }

    public static int soilColor(String s) {
        if ("optimal".equals(s)) return 0xFF2E7D32;
        if ("fair".equals(s)) return 0xFFF9A825;
        if ("action_needed".equals(s)) return 0xFFD32F2F;
        return 0xFF5C6B5F;
    }
}
