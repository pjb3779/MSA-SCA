package buaa.msasca.sca.core.application.error;

public class ToolExecutionException extends DomainException {

    private final String toolName;
    private final Long toolRunId;

    public ToolExecutionException(String toolName, Long toolRunId, String message) {
        super("TOOL_EXECUTION_FAILED", message);
        this.toolName = toolName;
        this.toolRunId = toolRunId;
    }

    public ToolExecutionException(String toolName, Long toolRunId, String message, Throwable cause) {
        super("TOOL_EXECUTION_FAILED", message, cause);
        this.toolName = toolName;
        this.toolRunId = toolRunId;
    }

    public String toolName() {
        return toolName;
    }

    public Long toolRunId() {
        return toolRunId;
    }

    public static ToolExecutionException mscan(Long toolRunId, String message) {
        return new ToolExecutionException("MSCAN", toolRunId, message);
    }

    public static ToolExecutionException mscan(Long toolRunId, String message, Throwable cause) {
        return new ToolExecutionException("MSCAN", toolRunId, message, cause);
    }

    public static ToolExecutionException codeql(Long toolRunId, String message) {
        return new ToolExecutionException("CODEQL", toolRunId, message);
    }

    public static ToolExecutionException codeql(Long toolRunId, String message, Throwable cause) {
        return new ToolExecutionException("CODEQL", toolRunId, message, cause);
    }
}

