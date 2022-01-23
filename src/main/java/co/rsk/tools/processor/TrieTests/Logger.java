package co.rsk.tools.processor.TrieTests;

import java.util.logging.Level;
public class Logger {
    private final java.util.logging.Logger impl;



    public Logger(String name) {
        this.impl = java.util.logging.Logger.getLogger(name);
    }

    public boolean isDebugEnabled() {
        return this.impl.isLoggable(Level.FINE);
    }

    public boolean isTraceEnabled() {
        return this.impl.isLoggable(Level.FINE);
    }

    public void debug(String s) {
        this.impl.log(Level.FINE, s);
    }

    public void debug(String s, Throwable e) {
        this.impl.log(Level.FINE, s, e);
    }

    public void debug(String s, Object... o) {
        this.impl.log(Level.FINE, s, o);
    }

    public void trace(String s) {
        this.impl.log(Level.FINE, s);
    }
    public void trace(String s, Object... o) {
        this.impl.log(Level.FINE, s, o);
    }

    public void error(String s) {
        this.impl.log(Level.SEVERE, s);
    }

    public void error(String s, Throwable e) {
        this.impl.log(Level.SEVERE, s, e);
    }

    public void error(String s, Object... o) {
        this.impl.log(Level.SEVERE, s, o);
    }

    public void warn(String s) {
        this.impl.log(Level.WARNING, s);
    }

    public void warn(String s, Throwable e) {
        this.impl.log(Level.WARNING, s, e);
    }
}
