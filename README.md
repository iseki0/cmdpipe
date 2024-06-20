# cmdpipe(Deprecated)

### Deprecated, don't use

[![Maven Central](https://img.shields.io/maven-central/v/space.iseki.cmdpipe/cmdpipe)](https://mvnrepository.com/artifact/space.iseki.cmdpipe/cmdpipe)


Utils for command line call

## Example

```kotlin
val r = cmdline("git", "clone", "https://github.com/iseki0/cmdpipe")
    .withWorkingDirectory(File("/dit/to/clone"))
    .handleStdout { input -> input.reader().readText() }
    .withTimeout(30_000)
    .execute()
check(r.exitCode == 0) { "Clone failed" }
println("stdout: ${r.stdoutValue}") // because we `readText`, so here's the text
println("stderr: ${r.stderrSnapshot}")
```

