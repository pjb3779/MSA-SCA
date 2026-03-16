package buaa.msasca.sca.app.worker.config.props;

import org.springframework.boot.context.properties.ConfigurationProperties;

import buaa.msasca.sca.core.port.out.tool.ToolLlmConfig;

@ConfigurationProperties(prefix = "sca.tool.llm")
public record ToolLlmProperties(
    String openaiApiKey,
    String openaiBaseUrl,
    String openaiModel
) implements ToolLlmConfig {

    // TODO(ops): 운영에서는 Secret YAML/Secret Store로 교체
    @Override
    public String openAiApiKey() {
        return openaiApiKey;
    }

    @Override
    public String openAiBaseUrl() {
        return (openaiBaseUrl == null || openaiBaseUrl.isBlank())
            ? "https://openrouter.ai/api/v1"
            : openaiBaseUrl;
    }

    @Override
    public String openAiModel() {
        return (openaiModel == null || openaiModel.isBlank())
            ? "deepseek/deepseek-r1"
            : openaiModel;
    }
}