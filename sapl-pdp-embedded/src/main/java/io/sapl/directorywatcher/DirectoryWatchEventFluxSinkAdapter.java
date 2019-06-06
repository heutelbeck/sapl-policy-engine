package io.sapl.directorywatcher;

import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.regex.Pattern;

import reactor.core.publisher.FluxSink;

/**
 * Adapter translating directory watch events into reactive flux events.
 */
public class DirectoryWatchEventFluxSinkAdapter
		implements DirectoryWatchEventConsumer<Path> {

	private final Pattern fileNamePattern;

	private FluxSink<WatchEvent<Path>> sink;

	private boolean canceled;

	/**
	 * Creates a new {@code DirectoryWatchEventFluxSinkAdapter} instance forwarding only
	 * those watch events to the flux sink that match the given file name pattern.
	 * @param fileNamePattern the file name pattern to be used to decide whether a watch
	 * event should be forwarded to the flux sink.
	 */
	public DirectoryWatchEventFluxSinkAdapter(Pattern fileNamePattern) {
		this.fileNamePattern = fileNamePattern;
	}

	/**
	 * Sets the flux sink to forward watch events to.
	 * @param sink the flux sink to be set.
	 */
	public void setSink(FluxSink<WatchEvent<Path>> sink) {
		this.sink = sink;
	}

	@Override
	public void onEvent(WatchEvent<Path> event) {
		final Path fileName = event.context();
		if (fileNamePattern.matcher(fileName.toString()).matches()) {
			sink.next(event);
		}
	}

	@Override
	public void onComplete() {
		sink.complete();
	}

	@Override
	public void cancel() {
		canceled = true;
	}

	@Override
	public boolean isCanceled() {
		return canceled;
	}

}
