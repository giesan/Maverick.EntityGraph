package io.av360.maverick.graph.feature.applications.api.dto;

public class Requests {

    public record RegisterApplicationRequest(String label, boolean persistent) {
    }

    public record CreateApiKeyRequest(String label) {
    }

    public record SetApplicationConfigRequest(String s3Host, String s3BucketId, String exportFrequency) {}

}
