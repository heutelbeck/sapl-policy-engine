package io.sapl.prp.filesystem;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

import reactor.core.publisher.FluxSink;

/**
 * Adapter translating directory watch events into reactive flux events.
 */
public class DirectoryWatchEventFluxSinkAdapter implements DirectoryWatchEventConsumer<Path> {

	private FluxSink<WatchEvent<Path>> sink;
	private boolean canceled;

	public void setSink(FluxSink<WatchEvent<Path>> sink) {
		this.sink = sink;
	}

	@Override
	public void onEvent(WatchEvent<Path> event) {
		@SuppressWarnings("unchecked")
		final Path filename = event.context();
		if (filename.toString().endsWith(FilesystemPolicyRetrievalPoint.POLICY_FILE_SUFFIX)) {
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
