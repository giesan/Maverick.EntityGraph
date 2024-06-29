CREATE TABLE IF NOT EXISTS entities (
    id SERIAL PRIMARY KEY,
    public_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    scope VARCHAR(255) DEFAULT 'default'
);

CREATE TABLE IF NOT EXISTS entity_attributes (
    id SERIAL PRIMARY KEY,
    attribute_name VARCHAR(255) NOT NULL,
    attribute_value JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    scope VARCHAR(255) DEFAULT 'default'
    );

CREATE TABLE IF NOT EXISTS attribute_annotations (
    id SERIAL PRIMARY KEY,
    annotation_name VARCHAR(255) NOT NULL,
    annotation_value JSONB,
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    scope VARCHAR(255) DEFAULT 'default'
    );