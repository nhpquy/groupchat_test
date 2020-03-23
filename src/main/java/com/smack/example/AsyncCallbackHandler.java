package com.smack.example;

public interface AsyncCallbackHandler<T> {
    void call(T result, Throwable exception);
}
