package org.apache.deltaspike.core.api.exception.control.event;
public class ExceptionEvent<T extends Throwable> {
    private final T exception;
    public ExceptionEvent(T exception) { this.exception = exception; }
    public T getException() { return exception; }
    public void handled() {}
    public void rethrow() {}
    public void abort() {}
    public void handledAndContinue() {}
}
