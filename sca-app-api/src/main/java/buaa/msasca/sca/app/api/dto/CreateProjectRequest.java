package buaa.msasca.sca.app.api.dto;

public record CreateProjectRequest(
    String name,
    String description,
    String gitUrl,
    String versionLabel
) {}