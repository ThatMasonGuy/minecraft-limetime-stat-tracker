package tempeststudios.lifetimestattracker.compat;

import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Path;

public final class ServerPathCompat {
    private ServerPathCompat() {
    }

    public static String serverDirectoryName(MinecraftServer server) {
        try {
            Method method = MinecraftServer.class.getMethod("getServerDirectory");
            Object directory = method.invoke(server);
            Path fileName = fileName(directory);
            if (fileName != null) {
                String value = fileName.toString();
                if (!value.isBlank()) {
                    return value;
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Path fileName(Object directory) {
        if (directory instanceof Path path) {
            return path.getFileName();
        }
        if (directory instanceof File file) {
            Path path = file.toPath();
            return path.getFileName();
        }
        return null;
    }
}
