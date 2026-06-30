package com.hospital.aibridge.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 医生端 RAG 配置。向量维度必须与 DashScope Embedding 模型的输出维度一致。
 */
@ConfigurationProperties(prefix = "hospital.ai.rag")
public class RagProperties {

    private boolean enabled = true;
    private boolean initializeSchema = true;
    private int dimensions = 1024;
    private int topK = 6;
    private double similarityThreshold = 0.55D;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInitializeSchema() {
        return initializeSchema;
    }

    public void setInitializeSchema(boolean initializeSchema) {
        this.initializeSchema = initializeSchema;
    }

    public int getDimensions() {
        return dimensions;
    }

    public void setDimensions(int dimensions) {
        this.dimensions = dimensions;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getSimilarityThreshold() {
        return similarityThreshold;
    }

    public void setSimilarityThreshold(double similarityThreshold) {
        this.similarityThreshold = similarityThreshold;
    }
}
