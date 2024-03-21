package space.iseki.cmdpipe;

public enum Stdio {
    STDIN(0), STDOUT(1), STDERR(2);
    final int i;

    Stdio(int i) {
        this.i = i;
    }

    public boolean isReadable() {
        return !isWriteable();
    }

    public boolean isWriteable() {
        return this == STDIN;
    }
}

