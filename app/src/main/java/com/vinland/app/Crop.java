package com.vinland.app;

import java.util.ArrayList;
import java.util.List;

public class Crop {

    private String id;
    private String name;
    private String scientificName;
    private String category;
    private String emoji;
    private String description;
    private double phMin;
    private double phMax;
    private double moistureMin;
    private double moistureMax;
    private double nitrogenMin;
    private double nitrogenMax;
    private double phosphorusMin;
    private double phosphorusMax;
    private double potassiumMin;
    private double potassiumMax;
    private List<String> monthlyTips;

    // Required empty constructor for Firestore deserialization
    public Crop() {
        this.monthlyTips = new ArrayList<>();
    }

    public Crop(String id, String name, String scientificName, String category, String emoji,
                String description, double phMin, double phMax, double moistureMin, double moistureMax,
                double nitrogenMin, double nitrogenMax, double phosphorusMin, double phosphorusMax,
                double potassiumMin, double potassiumMax, List<String> monthlyTips) {
        this.id = id;
        this.name = name;
        this.scientificName = scientificName;
        this.category = category;
        this.emoji = emoji;
        this.description = description;
        this.phMin = phMin;
        this.phMax = phMax;
        this.moistureMin = moistureMin;
        this.moistureMax = moistureMax;
        this.nitrogenMin = nitrogenMin;
        this.nitrogenMax = nitrogenMax;
        this.phosphorusMin = phosphorusMin;
        this.phosphorusMax = phosphorusMax;
        this.potassiumMin = potassiumMin;
        this.potassiumMax = potassiumMax;
        this.monthlyTips = monthlyTips != null ? monthlyTips : new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getScientificName() {
        return scientificName;
    }

    public void setScientificName(String scientificName) {
        this.scientificName = scientificName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getEmoji() {
        return emoji;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getPhMin() {
        return phMin;
    }

    public void setPhMin(double phMin) {
        this.phMin = phMin;
    }

    public double getPhMax() {
        return phMax;
    }

    public void setPhMax(double phMax) {
        this.phMax = phMax;
    }

    public double getMoistureMin() {
        return moistureMin;
    }

    public void setMoistureMin(double moistureMin) {
        this.moistureMin = moistureMin;
    }

    public double getMoistureMax() {
        return moistureMax;
    }

    public void setMoistureMax(double moistureMax) {
        this.moistureMax = moistureMax;
    }

    public double getNitrogenMin() {
        return nitrogenMin;
    }

    public void setNitrogenMin(double nitrogenMin) {
        this.nitrogenMin = nitrogenMin;
    }

    public double getNitrogenMax() {
        return nitrogenMax;
    }

    public void setNitrogenMax(double nitrogenMax) {
        this.nitrogenMax = nitrogenMax;
    }

    public double getPhosphorusMin() {
        return phosphorusMin;
    }

    public void setPhosphorusMin(double phosphorusMin) {
        this.phosphorusMin = phosphorusMin;
    }

    public double getPhosphorusMax() {
        return phosphorusMax;
    }

    public void setPhosphorusMax(double phosphorusMax) {
        this.phosphorusMax = phosphorusMax;
    }

    public double getPotassiumMin() {
        return potassiumMin;
    }

    public void setPotassiumMin(double potassiumMin) {
        this.potassiumMin = potassiumMin;
    }

    public double getPotassiumMax() {
        return potassiumMax;
    }

    public void setPotassiumMax(double potassiumMax) {
        this.potassiumMax = potassiumMax;
    }

    public List<String> getMonthlyTips() {
        return monthlyTips;
    }

    public void setMonthlyTips(List<String> monthlyTips) {
        this.monthlyTips = monthlyTips;
    }
}
