package run.jiewen.software.ai;

import lombok.Data;
import run.jiewen.software.extension.SoftwareApp;

@Data
public class SoftwareAppAiSuggestionRequest {

    private SoftwareApp app;

    private String instruction;

    private String target;

    public SoftwareAiTarget target() {
        return SoftwareAiTarget.from(target);
    }
}
