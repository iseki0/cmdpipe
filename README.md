# cmdpipe

![Maven Central](https://img.shields.io/maven-central/v/space.iseki.cmdpipe/cmdpipe)


Utils for commandline call

## Example

```kotlin
val r = cmdline("git", "clone", "https://github.com/iseki0/cmdpipe")
    .withWorkingDirectory(File("/dit/to/clone"))
    .handleStdout { input -> input.reader().readText() }
    .withTimeout(30_000)
    .execute()
check(r.exitCode == 0) { "Clone failed" }
println("stdout: ${r.stdoutValue}") // because we `readText`, so there's the text
println("stderr: ${r.stderrSnapshot}")
```

