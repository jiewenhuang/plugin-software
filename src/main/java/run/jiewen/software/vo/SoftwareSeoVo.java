package run.jiewen.software.vo;

import java.util.List;
import lombok.Builder;
import lombok.Value;
import run.jiewen.software.extension.SoftwareApp;

@Value
@Builder
public class SoftwareSeoVo {

    String title;

    String description;

    List<String> keywords;

    public static SoftwareSeoVo from(SoftwareApp.Seo seo) {
        if (seo == null) {
            return null;
        }
        return SoftwareSeoVo.builder()
            .title(seo.getTitle())
            .description(seo.getDescription())
            .keywords(seo.getKeywords())
            .build();
    }
}
