package run.jiewen.software.download;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DownloadTargetPolicyTest {

    @Test
    void acceptsHttpUrlsAndSafeSitePaths() {
        assertThat(DownloadTargetPolicy.usable(" https://downloads.example.com/app.dmg "))
            .isEqualTo("https://downloads.example.com/app.dmg");
        assertThat(DownloadTargetPolicy.usable("http://localhost:8090/file.zip"))
            .isEqualTo("http://localhost:8090/file.zip");
        assertThat(DownloadTargetPolicy.usable("/upload/software/app.dmg?token=1"))
            .isEqualTo("/upload/software/app.dmg?token=1");
    }

    @Test
    void rejectsActiveContentAndSchemeRelativeTargets() {
        assertThat(DownloadTargetPolicy.usable("javascript:alert(1)")).isNull();
        assertThat(DownloadTargetPolicy.usable("file:///etc/passwd")).isNull();
        assertThat(DownloadTargetPolicy.usable("//evil.example/file.dmg")).isNull();
        assertThat(DownloadTargetPolicy.usable("/\\evil.example/file.dmg")).isNull();
        assertThat(DownloadTargetPolicy.usable("https:/missing-authority")).isNull();
    }

    @Test
    void skipsInvalidOverridesAndUsesTheFirstSafeFallback() {
        assertThat(DownloadTargetPolicy.firstUsable(
            "javascript:alert(1)", "https://downloads.example.com/app.dmg"))
            .isEqualTo("https://downloads.example.com/app.dmg");
    }
}
