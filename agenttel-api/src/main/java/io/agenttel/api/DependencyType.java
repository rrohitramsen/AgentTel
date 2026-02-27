package io.agenttel.api;

public enum DependencyType {
    INTERNAL_SERVICE("internal_service"),
    EXTERNAL_API("external_api"),
    DATABASE("database"),
    MESSAGE_BROKER("message_broker"),
    CACHE("cache"),
    OBJECT_STORE("object_store"),
    IDENTITY_PROVIDER("identity_provider");

    private final String value;

    DependencyType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DependencyType fromValue(String value) {
        for (DependencyType type : values()) {
            if (type.value.equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DependencyType: " + value);
    }
}
