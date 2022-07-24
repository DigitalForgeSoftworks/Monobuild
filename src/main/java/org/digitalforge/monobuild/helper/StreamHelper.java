package org.digitalforge.monobuild.helper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.CompletableFuture;
import javax.inject.Singleton;

import org.eclipse.jgit.util.io.TeeOutputStream;

import org.digitalforge.sneakythrow.SneakyThrow;

@Singleton
public class StreamHelper {

    public CompletableFuture<String> forkToFileAndString(InputStream stream, Path path) {

        return CompletableFuture.supplyAsync(() -> {
            try (OutputStream file = Files.newOutputStream(path, StandardOpenOption.CREATE)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                TeeOutputStream tee = new TeeOutputStream(file, output);
                stream.transferTo(tee);
                return output.toString();
            } catch (IOException e) {
                throw SneakyThrow.sneak(e);
            }
        });

    }

}
