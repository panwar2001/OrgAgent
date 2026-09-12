package com.panwar2001.orgagent.core.config;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.TaskExecutorAdapter;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;

import lombok.extern.slf4j.Slf4j;

/**
 * Asynchronous work runs on virtual threads.
 *
 * <p>A chat turn fans out into independent writes — the rolling Redis window, the Postgres
 * integration log and the semantic cache — none of which should make the user wait for their
 * answer. Virtual threads keep that fan-out cheap: one thread per task instead of a pool sized for
 * the slowest downstream.
 *
 * <p>{@code spring.threads.virtual.enabled=true} already makes Tomcat and Boot's own
 * {@code applicationTaskExecutor} virtual-thread based; this class gives {@code @Async} the same
 * behaviour instead of the thread-pool executor Spring would otherwise default to.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

	static final String THREAD_NAME_PREFIX = "orgagent-async-";

	@Override
	public Executor getAsyncExecutor() {
		return new TaskExecutorAdapter(
				Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name(THREAD_NAME_PREFIX, 0).factory()));
	}

	@Override
	public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
		return (Throwable failure, Method method, Object... parameters) -> log
			.error("Async task {}.{} failed", method.getDeclaringClass().getSimpleName(), method.getName(), failure);
	}

}
