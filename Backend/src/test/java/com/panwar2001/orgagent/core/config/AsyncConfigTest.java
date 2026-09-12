package com.panwar2001.orgagent.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class AsyncConfigTest {

	private final AsyncConfig config = new AsyncConfig();

	@Test
	void runsAsyncTasksOnVirtualThreads() throws InterruptedException {
		Executor executor = config.getAsyncExecutor();
		AtomicBoolean virtual = new AtomicBoolean(false);
		AtomicReference<String> threadName = new AtomicReference<>();
		CountDownLatch done = new CountDownLatch(1);

		executor.execute(() -> {
			virtual.set(Thread.currentThread().isVirtual());
			threadName.set(Thread.currentThread().getName());
			done.countDown();
		});

		assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(virtual).isTrue();
		assertThat(threadName.get()).startsWith(AsyncConfig.THREAD_NAME_PREFIX);
	}

	@Test
	void doesNotReuseThreadsBetweenTasks() throws InterruptedException {
		Executor executor = config.getAsyncExecutor();
		AtomicReference<Thread> first = new AtomicReference<>();
		AtomicReference<Thread> second = new AtomicReference<>();
		CountDownLatch both = new CountDownLatch(2);

		executor.execute(() -> {
			first.set(Thread.currentThread());
			both.countDown();
		});
		executor.execute(() -> {
			second.set(Thread.currentThread());
			both.countDown();
		});

		assertThat(both.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(first.get()).isNotSameAs(second.get());
	}

	@Test
	void providesAHandlerForFailuresThatEscapeAnAsyncTask() throws Exception {
		Method method = AsyncConfigTest.class.getDeclaredMethod("asyncWorkThatFails");
		var handler = config.getAsyncUncaughtExceptionHandler();

		assertThat(handler).isNotNull();
		assertThatCode(() -> handler.handleUncaughtException(new IllegalStateException("redis down"), method))
			.doesNotThrowAnyException();
	}

	@SuppressWarnings("unused")
	private void asyncWorkThatFails() {
	}

}
