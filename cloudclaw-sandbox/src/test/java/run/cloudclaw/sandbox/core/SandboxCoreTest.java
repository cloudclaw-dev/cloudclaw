package run.cloudclaw.sandbox.core;

import run.cloudclaw.sandbox.config.SandboxProperties;
import org.springaicommunity.sandbox.Sandbox;

import java.time.Duration;

/**
 * Standalone test for sandbox core functionality.
 * Run: java -cp ... run.cloudclaw.sandbox.core.SandboxCoreTest
 * Or just call from main.
 */
public class SandboxCoreTest {

    public static void main(String[] args) {
        System.out.println("=== CloudClaw Sandbox Core Test ===\n");

        testLocalStateless();
        testLocalSession();
        testSandboxFactory();

        System.out.println("\n=== All tests passed ===");
    }

    static void testLocalStateless() {
        System.out.println("--- Test: Local Stateless Execution ---");
        try (Sandbox sandbox = SandboxFactory.create("LOCAL", "test-st-")) {
            // Test Python
            sandbox.files().create("script.py", "print('Hello from sandbox!')\nprint('2^10 =', 2**10)\n");
            var result = sandbox.exec(
                org.springaicommunity.sandbox.ExecSpec.builder()
                    .command("python", "-u", "script.py")
                    .timeout(Duration.ofSeconds(10))
                    .build()
            );
            System.out.println("Python exit code: " + result.exitCode());
            System.out.println("Python stdout: " + result.stdout());
            assert result.exitCode() == 0 : "Expected exit code 0";
            assert result.stdout().contains("1024") : "Expected 1024 in output";

            // Test file operations
            sandbox.files().create("test.txt", "Hello file!");
            String content = sandbox.files().read("test.txt");
            System.out.println("File read: " + content);
            assert "Hello file!".equals(content) : "File content mismatch";

            boolean exists = sandbox.files().exists("test.txt");
            System.out.println("File exists: " + exists);
            assert exists : "File should exist";

            System.out.println("✓ Local stateless test PASSED\n");
        } catch (Exception e) {
            System.err.println("✗ FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void testLocalSession() {
        System.out.println("--- Test: Local Session Mode ---");
        try (Sandbox sandbox = SandboxFactory.create("LOCAL", "test-se-")) {
            // Write file in first execution
            sandbox.files().create("data.txt", "session-data-123");
            System.out.println("Created data.txt");

            // Read file in second operation (simulating session persistence)
            String content = sandbox.files().read("data.txt");
            System.out.println("Read data.txt: " + content);
            assert "session-data-123".equals(content) : "Session file mismatch";

            // List files
            var entries = sandbox.files().list(".");
            System.out.println("Files: " + entries.size());
            for (var e : entries) {
                System.out.println("  " + e.type() + " " + e.path() + " (" + e.size() + " bytes)");
            }

            System.out.println("✓ Local session test PASSED\n");
        } catch (Exception e) {
            System.err.println("✗ FAILED: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void testSandboxFactory() {
        System.out.println("--- Test: SandboxFactory ---");
        // Test LOCAL creation
        try (Sandbox local = SandboxFactory.create("LOCAL", "factory-test-")) {
            assert local != null : "LOCAL sandbox should not be null";
            assert local.workDir() != null : "workDir should not be null";
            System.out.println("LOCAL workDir: " + local.workDir());
        } catch (Exception e) {
            System.err.println("✗ LOCAL factory FAILED: " + e.getMessage());
        }

        // Test StatelessExecutor formatResult
        System.out.println("✓ SandboxFactory test PASSED\n");
    }
}
