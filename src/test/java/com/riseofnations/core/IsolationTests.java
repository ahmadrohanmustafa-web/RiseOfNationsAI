package com.riseofnations.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static com.riseofnations.core.TestKit.check;
import static com.riseofnations.core.TestKit.eq;
import static com.riseofnations.core.TestKit.test;

/**
 * Source-level safety checks. They cannot prove the game never crashes, but they do catch the two
 * most common dedicated-server mistakes: client classes imported into shared code, and a missing
 * entrypoint class.
 */
final class IsolationTests {

    private IsolationTests() { }

    private static final Path SRC = Path.of("src/main/java");

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> walk = Files.walk(SRC)) {
            return walk.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    static void run() {
        System.out.println("Server/client isolation");

        test("source folder is found (so the checks below are real)", () -> {
            check(Files.isDirectory(SRC), "src/main/java not found from " + Path.of("").toAbsolutePath());
            check(javaFiles().size() > 20, "expected the project's source files");
        });

        test("no client-only class is referenced outside the client package", () -> {
            List<String> violations = new ArrayList<>();
            for (Path p : javaFiles()) {
                String norm = p.toString().replace('\\', '/');
                if (norm.contains("/com/riseofnations/client/")) continue;
                String text = Files.readString(p, StandardCharsets.UTF_8);
                for (String banned : List.of("net.minecraft.client", "net.fabricmc.fabric.api.client",
                        "net.fabricmc.api.ClientModInitializer", "com.riseofnations.client", "com.mojang.blaze3d")) {
                    if (text.contains(banned)) violations.add(norm + " mentions " + banned);
                }
            }
            eq(List.of(), violations, "client references in shared code");
        });

        test("core game logic has no Minecraft or Fabric imports at all", () -> {
            List<String> violations = new ArrayList<>();
            for (Path p : javaFiles()) {
                String norm = p.toString().replace('\\', '/');
                if (!norm.contains("/com/riseofnations/core/")) continue;
                String text = Files.readString(p, StandardCharsets.UTF_8);
                for (String banned : List.of("import net.minecraft", "import net.fabricmc", "import com.mojang")) {
                    if (text.contains(banned)) violations.add(norm + " has " + banned);
                }
            }
            eq(List.of(), violations, "Minecraft imports in core");
        });

        test("fabric.mod.json entrypoints point at classes that exist", () -> {
            String json = Files.readString(Path.of("src/main/resources/fabric.mod.json"), StandardCharsets.UTF_8);
            for (String cls : List.of("com.riseofnations.RiseOfNationsMod", "com.riseofnations.client.RiseOfNationsClient")) {
                check(json.contains("\"" + cls + "\""), "declared: " + cls);
                check(Files.exists(SRC.resolve(cls.replace('.', '/') + ".java")), "file exists: " + cls);
            }
            check(json.contains("\"main\"") && json.contains("\"client\""), "both entrypoint kinds present");
        });
    }
}
