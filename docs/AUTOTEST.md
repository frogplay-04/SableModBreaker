# SableMobBreaker AutoTester

## Automatic checks

The Windows workflow runs on pushes and manual dispatch using the self-hosted Windows x64 runner.

1. Build the mod with Java 21 and NeoForge 21.1.250.
2. Run the dedicated NeoForge GameTest server.
3. Reject an empty test registry and verify all eight expected GameTest method names appear in the server log.
4. Upload build output, logs, crash reports, JVM diagnostics, and a Markdown summary.

GameTests are explicitly registered through `RegisterGameTestsEvent`. The dedicated GameTest run disables Gradle's forced process exit so the workflow can collect the real result.

## Manual client smoke test

Use **Actions → Windows Minecraft Test → Run workflow** to launch the Minecraft client. The workflow waits up to eight minutes for the normal `Done (` startup marker. When it appears, startup is marked successful and the test process is stopped as cleanup. If it does not appear, the workflow reports a timeout or early exit, captures logs and JVM thread diagnostics where possible, and only then stops the process. A forced cleanup is not reported as proof of a Minecraft crash.

## Current server-side coverage

- block HP initialization, exact 5 HP damage, manager persistence, and reset;
- destroyed-block state and block-entity NBT retention;
- HP isolation between sublevels;
- dead-block tracking, HP clamping, and air having zero HP;
- oxygen tank capacity clamping;
- plasma cutter enabled-state item data;
- oxygen tank data stored by the welding torch and plasma cutter.

## Known coverage limits

Server GameTests do not simulate real mouse/keyboard input, client rendering, GUI layout, animation, or full Sable sublevel ray-tracing interactions. The manual client workflow checks startup only. Those areas still need client-side automation and/or a human gameplay checklist.
