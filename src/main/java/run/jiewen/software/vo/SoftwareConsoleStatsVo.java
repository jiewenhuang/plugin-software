package run.jiewen.software.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SoftwareConsoleStatsVo {

    long total;

    long published;

    long drafts;

    long downloads;

    List<String> platforms;
}
