# SableMobBreaker AutoTester

## What runs automatically

The Windows Minecraft Test workflow runs on every push to `main`, every pull request targeting `main`, and on manual dispatch.

1. Compile and package the mod with Java 21 / NeoForge 21.1.250.
2. Run the dedicated NeoForge GameTest server.
3. Save the build, Minecraft logs, crash reports, and a Markdown test summary as a GitHub Actions artifact.

The workflow uses the repository's self-hosted Windows x64 runner. The runner must be online (the Actions runner process must be listening) for automatic jobs to start.

## Manual client smoke test

Use **Actions → Windows Minecraft Test → Run workflow** to additionally launch the Minecraft client and verify that it reaches the normal `Done (` startup marker. The client is stopped after the startup window so the runner is released.

## Current automated coverage

Server-side GameTests currently cover:

- block HP initialization, exact 5 HP damage, persistence in the manager, and reset;
- saved destroyed-block state and block-entity NBT payload;
- HP isolation between sublevels;
- dead-block tracking, HP clamping, and air having zero HP;
- oxygen tank capacity clamping;
- plasma cutter enabled-state item data;
- oxygen tank data stored by the welding torch and plasma cutter.

## Known coverage limits

A successful build is not proof that all gameplay works. Server GameTests do not simulate real mouse/keyboard input, client rendering, GUI layout, animation, or the full Sable sublevel ray-tracing interaction. The manual client workflow currently checks startup only. Those areas need client-side automation and/or a human gameplay checklist before the project can claim full end-to-end coverage.
