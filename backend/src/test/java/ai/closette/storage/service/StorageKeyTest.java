package ai.closette.storage.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The uploaded filename is the only client-controlled part of an object key, so
 * what survives from it is worth pinning down.
 */
class StorageKeyTest {

    private String extensionOf(String name) throws Exception {
        Method method = StorageService.class.getDeclaredMethod("extensionOf", String.class);
        method.setAccessible(true);
        return (String) method.invoke(null, name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "PHOTO.JPG", "a.png", "shot.heic", "x.webp"})
    void realImageExtensionsAreKept(String name) throws Exception {
        assertThat(extensionOf(name)).startsWith(".");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "photo.jpg/../../other-user/key",
            "photo.jpg/nested",
            "photo.../etc/passwd",
            "photo.sh",
            "photo.jpg%2F..",
    })
    void anythingThatCouldShapeTheKeyIsDropped(String name) throws Exception {
        // A crafted name must never contribute a separator or a traversal step;
        // the key stays "<userId>/<uuid>" with at most a known extension.
        assertThat(extensionOf(name)).doesNotContain("/").doesNotContain("..");
    }

    @Test
    void aMissingOrExtensionlessNameIsFine() throws Exception {
        assertThat(extensionOf(null)).isEmpty();
        assertThat(extensionOf("no-extension")).isEmpty();
    }
}
