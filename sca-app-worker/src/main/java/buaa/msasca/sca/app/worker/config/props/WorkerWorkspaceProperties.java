package buaa.msasca.sca.app.worker.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sca.workspace")
public record WorkerWorkspaceProperties(
        String basePath
    ) {
    /**
     * basePath가 비어있으면 기본값을 쓰도록 보정한다.
     *
     * @return workspace base path
     */
    public String basePath() {
        if (basePath == null || basePath.isBlank()) {
        return ".";
        }
        return basePath;
    }
}
