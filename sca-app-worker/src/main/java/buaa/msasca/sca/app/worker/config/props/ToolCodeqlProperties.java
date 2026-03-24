package buaa.msasca.sca.app.worker.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import buaa.msasca.sca.core.port.out.tool.CodeqlConfig;

@ConfigurationProperties(prefix = "sca.tool.codeql")
public record ToolCodeqlProperties(
    /**
     * codeql database analyze 시 --ram 옵션 (MB 단위).
     * 비어 있으면 CodeQL 기본값(약 734MB) 사용.
     * OOM 발생 시 4096 이상 권장. 16GB RAM 노트북에서는 4096~8192 권장.
     */
    String analyzeRam
) implements CodeqlConfig {

    @Override
    public String analyzeRam() {
        return analyzeRam;
    }
}
