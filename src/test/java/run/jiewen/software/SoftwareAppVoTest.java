package run.jiewen.software;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import run.halo.app.extension.Metadata;
import run.jiewen.software.extension.SoftwareApp;
import run.jiewen.software.vo.SoftwareAppDetailVo;
import run.jiewen.software.vo.SoftwareAppVo;

class SoftwareAppVoTest {

    @Test
    void mapsPublicFrontendFieldsFromSpecAndStatus() {
        var vo = SoftwareAppVo.from(app());

        assertThat(vo.getDescription()).isEqualTo("<p>Clean your Mac.</p>");
        assertThat(vo.getOfficialWebsite()).isEqualTo("https://example.com");
        assertThat(vo.getPlatform()).isEqualTo("macos");
        assertThat(vo.getArchitectures()).containsExactly("arm64", "x64");
        assertThat(vo.getLicenseType()).isEqualTo("commercial");
        assertThat(vo.getLocale()).isEqualTo("zh-CN");
        assertThat(vo.getFeatured()).isTrue();
        assertThat(vo.getRecommended()).isTrue();
        assertThat(vo.getPublishedAt()).isEqualTo(Instant.parse("2026-06-01T10:00:00Z"));
        assertThat(vo.getSeo().getTitle()).isEqualTo("CleanMyMac X for Mac");
        assertThat(vo.getLatestVersionName()).isEqualTo("version-1");
        assertThat(vo.getLatestReleaseDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(vo.getMinOsVersion()).isEqualTo("10.13");
        assertThat(vo.getSizeBytes()).isEqualTo(157286400L);
        assertThat(vo.getCommentCount()).isEqualTo(12L);
    }

    @Test
    void detailVoKeepsListFieldsForFrontendDetailPage() {
        var detail = SoftwareAppDetailVo.from(SoftwareAppVo.from(app()), List.of(), List.of());

        assertThat(detail.getDescription()).isEqualTo("<p>Clean your Mac.</p>");
        assertThat(detail.getLatestReleaseDate()).isEqualTo(LocalDate.of(2026, 6, 20));
        assertThat(detail.getMinOsVersion()).isEqualTo("10.13");
        assertThat(detail.getArchitectures()).containsExactly("arm64", "x64");
        assertThat(detail.getSizeBytes()).isEqualTo(157286400L);
        assertThat(detail.getCommentCount()).isEqualTo(12L);
    }

    private static SoftwareApp app() {
        var metadata = new Metadata();
        metadata.setName("software-generated-name");

        var seo = new SoftwareApp.Seo();
        seo.setTitle("CleanMyMac X for Mac");
        seo.setDescription("Mac cleaner");
        seo.setKeywords(List.of("cleaner", "mac"));

        var spec = new SoftwareApp.Spec();
        spec.setSlug("cleanmymac-x");
        spec.setDisplayName("CleanMyMac X");
        spec.setSubtitle("Mac cleaner");
        spec.setOfficialWebsite("https://example.com");
        spec.setSummary("Clean, optimize and maintain your Mac.");
        spec.setDescription("<p>Clean your Mac.</p>");
        spec.setPlatform("macos");
        spec.setArchitectures(List.of("arm64", "x64"));
        spec.setLicenseType("commercial");
        spec.setLocale("zh-CN");
        spec.setFeatured(true);
        spec.setRecommended(true);
        spec.setPublishedAt(Instant.parse("2026-06-01T10:00:00Z"));
        spec.setSeo(seo);

        var status = new SoftwareApp.Status();
        status.setLatestVersionName("version-1");
        status.setLatestVersion("4.15.0");
        status.setLatestReleaseDate(LocalDate.of(2026, 6, 20));
        status.setMinOsVersion("10.13");
        status.setSizeBytes(157286400L);
        status.setDownloadCount(42L);
        status.setLikeCount(8L);
        status.setCommentCount(12L);

        var app = new SoftwareApp();
        app.setMetadata(metadata);
        app.setSpec(spec);
        app.setStatus(status);
        return app;
    }
}
