package org.av360.maverick.graph.model.enums;

public enum RepositoryType {
    ENTITIES,
    SCHEMA,
    TRANSACTIONS,
    APPLICATION,
    CLASSIFIER;


    @Override
    public String toString() {
        return name().toLowerCase();
    }




}