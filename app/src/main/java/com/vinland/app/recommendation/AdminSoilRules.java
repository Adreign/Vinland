package com.vinland.app.recommendation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Advice rules the admin writes on the web admin "Recommendation Management" page
 * (Firestore collection {@code recommendations}). Each rule says: when a soil
 * parameter meets a condition (like "pH < 5.5"), show this advice, optionally only
 * for some crops.
 */
public final class AdminSoilRules {

    public enum Param { MOISTURE, PH, NITROGEN, PHOSPHORUS, POTASSIUM }

    public static final class Rule {
        public final String title;
        public final String status;
        public final String advice;
        public final String severity;
        public final Param param;
        public final String condition;
        /** Lowercase crop names; empty means the rule applies to every crop. */
        public final List<String> crops;

        Rule(String title, String status, String advice, String severity, Param param,
             String condition, List<String> crops) {
            this.title = title;
            this.status = status;
            this.advice = advice;
            this.severity = severity;
            this.param = param;
            this.condition = condition;
            this.crops = crops;
        }

        public boolean appliesToAllCrops() {
            return crops.isEmpty();
        }

        public boolean appliesTo(String cropName) {
            return crops.isEmpty() || (cropName != null && crops.contains(cropName.trim().toLowerCase(Locale.ROOT)));
        }

        public boolean matches(double moisture, double ph, double n, double p, double k) {
            double value;
            switch (param) {
                case MOISTURE: value = moisture; break;
                case PH: value = ph; break;
                case NITROGEN: value = n; break;
                case PHOSPHORUS: value = p; break;
                default: value = k; break;
            }
            return conditionMatches(condition, value);
        }

        /** Short label like "Soil pH < 5.5". */
        public String triggerText() {
            return paramLabel(param) + " " + condition;
        }
    }

    private AdminSoilRules() {
    }

    /** Builds a rule from a Firestore document; returns null if it is unusable. */
    public static Rule fromMap(Map<String, Object> d) {
        if (d == null) return null;
        Param param = parseParam(str(d.get("parameter")));
        String condition = str(d.get("condition"));
        String advice = str(d.get("advice"));
        if (param == null || condition.isEmpty() || advice.isEmpty()) return null;
        if (!isValidCondition(condition)) return null;

        List<String> crops = new ArrayList<>();
        Object raw = d.get("applicableCrops");
        if (raw instanceof List) {
            for (Object o : (List<?>) raw) {
                String c = str(o).toLowerCase(Locale.ROOT);
                if (!c.isEmpty()) crops.add(c);
            }
        }
        return new Rule(str(d.get("title")), str(d.get("status")), advice,
                str(d.get("severity")), param, condition, Collections.unmodifiableList(crops));
    }

    /** Rules that fire for these readings and this crop (crop may be null = general soil check). */
    public static List<Rule> matching(List<Rule> rules, String cropName,
                                      double moisture, double ph, double n, double p, double k) {
        List<Rule> out = new ArrayList<>();
        if (rules == null) return out;
        for (Rule r : rules) {
            boolean cropOk = cropName == null ? r.appliesToAllCrops() : r.appliesTo(cropName);
            if (cropOk && r.matches(moisture, ph, n, p, k)) out.add(r);
        }
        return out;
    }

    static Param parseParam(String s) {
        String t = s.toLowerCase(Locale.ROOT);
        if (t.contains("phosph") || t.equals("p") || t.contains("(p)")) return Param.PHOSPHORUS;
        if (t.contains("potass") || t.equals("k") || t.contains("(k)")) return Param.POTASSIUM;
        if (t.contains("nitro") || t.equals("n") || t.contains("(n)")) return Param.NITROGEN;
        if (t.contains("moist")) return Param.MOISTURE;
        if (t.contains("ph")) return Param.PH;
        return null;
    }

    static String paramLabel(Param p) {
        switch (p) {
            case MOISTURE: return "Soil Moisture";
            case PH: return "Soil pH";
            case NITROGEN: return "Nitrogen (N)";
            case PHOSPHORUS: return "Phosphorus (P)";
            default: return "Potassium (K)";
        }
    }

    // "< 5.5", "<= 30", "> 7", ">= 80", "= 6", "5.5 - 6.5", "5.5 to 6.5"
    private static final Pattern COMPARE = Pattern.compile("^(<=|>=|<|>|=|==)\\s*(-?\\d+(?:\\.\\d+)?)\\s*%?(?:\\s*ppm)?$");
    private static final Pattern BETWEEN = Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*%?\\s*(?:-|–|to)\\s*(-?\\d+(?:\\.\\d+)?)\\s*%?(?:\\s*ppm)?$");

    static boolean isValidCondition(String condition) {
        String c = condition.trim().toLowerCase(Locale.ROOT);
        return COMPARE.matcher(c).matches() || BETWEEN.matcher(c).matches();
    }

    static boolean conditionMatches(String condition, double value) {
        String c = condition.trim().toLowerCase(Locale.ROOT);
        Matcher m = COMPARE.matcher(c);
        if (m.matches()) {
            double x = Double.parseDouble(m.group(2));
            switch (m.group(1)) {
                case "<": return value < x;
                case "<=": return value <= x;
                case ">": return value > x;
                case ">=": return value >= x;
                default: return value == x;
            }
        }
        m = BETWEEN.matcher(c);
        if (m.matches()) {
            double a = Double.parseDouble(m.group(1));
            double b = Double.parseDouble(m.group(2));
            return value >= Math.min(a, b) && value <= Math.max(a, b);
        }
        return false;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().trim();
    }
}
