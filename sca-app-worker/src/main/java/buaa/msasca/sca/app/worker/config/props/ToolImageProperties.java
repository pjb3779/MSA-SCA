package buaa.msasca.sca.app.worker.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import buaa.msasca.sca.core.port.out.tool.ToolImageConfig;

@ConfigurationProperties(prefix = "sca.tool.images")
public record ToolImageProperties(
    String codeql,
    String mscan,
    String agent
) implements ToolImageConfig {

    @Override
    public String codeqlImage() {
        return codeql;
    }

    @Override
    public String mscanImage() {
        return mscan;
    }

    @Override
    public String agentImage() {
        return agent;
    }
}