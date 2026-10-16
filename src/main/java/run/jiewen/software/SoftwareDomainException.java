package run.jiewen.software;

import org.springframework.web.server.ServerWebInputException;

/** Stable business reasons without coupling the domain to a transport. */
public class SoftwareDomainException extends ServerWebInputException {
    private final String code;

    public SoftwareDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
