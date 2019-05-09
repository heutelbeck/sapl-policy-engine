package io.sapl.directorywatcher;

import java.nio.file.WatchEvent;

/**
 * Consumes directory watch events emitted by the {@link DirectoryWatcher} and is called
 * when the watch service has completed or when the watched directory is no longer
 * accessible.
 *
 * @param <T> the type of the watch event's context
 */
public interface DirectoryWatchEventConsumer<T> {

	/**
	 * Called upon each watch event emitted by the directory watch service registered to
	 * the policy directory.
	 * @param event the directory watch event.
	 */
	void onEvent(WatchEvent<T> event);

	/**
	 * Called when the watch service has completed.
	 */
	void onComplete();

	/**
	 * Called when the watched directory is no longer accessible.
	 */
	void cancel();

	/**
	 * @return {@code true} if {@link #cancel()} has been called, {@code false} otherwise.
	 */
	boolean isCanceled();

}
