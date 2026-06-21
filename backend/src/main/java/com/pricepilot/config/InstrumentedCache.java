package com.pricepilot.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.cache.Cache;

import java.util.concurrent.Callable;

public class InstrumentedCache implements Cache {
    private final Cache delegate;
    private final MeterRegistry registry;
    private final String cacheName;

    public InstrumentedCache(Cache delegate, MeterRegistry registry) {
        this.delegate = delegate;
        this.registry = registry;
        this.cacheName = delegate.getName();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper wrapper = delegate.get(key);
        if (wrapper != null) {
            registry.counter("pricepilot.cache.hits", "cache", cacheName).increment();
        } else {
            registry.counter("pricepilot.cache.misses", "cache", cacheName).increment();
        }
        return wrapper;
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        T value = delegate.get(key, type);
        if (value != null) {
            registry.counter("pricepilot.cache.hits", "cache", cacheName).increment();
        } else {
            registry.counter("pricepilot.cache.misses", "cache", cacheName).increment();
        }
        return value;
    }

    @Override
    public <T> T get(Object key, Callable<T> valueLoader) {
        // Fallback: valueLoader is typically used for sync cache loading (miss by design if loader runs)
        return delegate.get(key, () -> {
            registry.counter("pricepilot.cache.misses", "cache", cacheName).increment();
            return valueLoader.call();
        });
    }

    @Override
    public void put(Object key, Object value) {
        delegate.put(key, value);
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
    }

    @Override
    public void clear() {
        delegate.clear();
    }
}
