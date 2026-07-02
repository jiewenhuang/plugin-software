package run.jiewen.software.ai;

import java.util.List;
import lombok.Data;
import run.jiewen.software.extension.SoftwareApp;

@Data
public class SoftwareAppAiSuggestion {

    private String summary;

    private String description;

    private SoftwareApp.Seo seo;

    private List<String> warnings;
}
