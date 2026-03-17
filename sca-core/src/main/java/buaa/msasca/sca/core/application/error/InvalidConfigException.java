package buaa.msasca.sca.core.application.error;

public class InvalidConfigException extends DomainException {

    public InvalidConfigException(String message) {
        super("INVALID_CONFIG", message);
    }
}

