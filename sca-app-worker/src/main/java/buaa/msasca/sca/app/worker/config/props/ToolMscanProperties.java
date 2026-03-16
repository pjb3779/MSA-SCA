package buaa.msasca.sca.app.worker.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sca.tool.mscan")
public record ToolMscanProperties(
    /**
     * Mscan Docker 컨테이너 메모리 제한 (예: 6g, 6144m).
     * 비어 있으면 제한 없음. 16GB RAM 노트북 등에서는 6g 권장.
     */
    String dockerMemory
) {

    public boolean hasDockerMemoryLimit() {
        return dockerMemory != null && !dockerMemory.isBlank();
    }
}
