package com.pricepilot.intelligence.semantic.contract;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * Value object representing the output of the Canonical Product Text Contract.
 * Encapsulates the deterministic canonical text string, representation version,
 * text hash, and structured semantic attributes.
 */
public final class CanonicalProductText implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String canonicalText;
    private final String version;
    private final String semanticHash;
    private final Map<String, String> attributes;

    public CanonicalProductText(
            String canonicalText,
            String version,
            String semanticHash,
            Map<String, String> attributes) {
        this.canonicalText = Objects.requireNonNull(canonicalText, "canonicalText cannot be null");
        this.version = Objects.requireNonNull(version, "version cannot be null");
        this.semanticHash = Objects.requireNonNull(semanticHash, "semanticHash cannot be null");
        this.attributes = (attributes != null) ? Collections.unmodifiableMap(attributes) : Collections.emptyMap();
    }

    public String getCanonicalText() {
        return canonicalText;
    }

    public String getVersion() {
        return version;
    }

    public String getSemanticHash() {
        return semanticHash;
    }

    public Map<String, String> getAttributes() {
        return attributes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CanonicalProductText that = (CanonicalProductText) o;
        return Objects.equals(canonicalText, that.canonicalText) &&
                Objects.equals(version, that.version) &&
                Objects.equals(semanticHash, that.semanticHash);
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonicalText, version, semanticHash);
    }

    @Override
    public String toString() {
        return "CanonicalProductText{" +
                "version='" + version + '\'' +
                ", semanticHash='" + semanticHash + '\'' +
                ", textLength=" + canonicalText.length() +
                '}';
    }
}
