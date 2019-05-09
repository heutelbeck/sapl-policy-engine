package io.sapl.directorywatcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * Directory watch fluxes are combined with PDP fluxes. Therefore an initial event is
 * required even if nothing has been changed in the directory. Otherwise the combined flux
 * would only emit items after the first file modification. This class defines the type of
 * the initial directory watch event. No PRP document index update or PDP reconfiguration
 * will be triggered upon this event.
 */
public class InitialWatchEvent implements WatchEvent<Path> {

	public static WatchEvent<Path> INSTANCE = new InitialWatchEvent();

	private InitialWatchEvent() {
		// singleton
	}

	@Override
	public Kind<Path> kind() {
		return ENTRY_MODIFY;
	}

	@Override
	public int count() {
		return 0;
	}

	@Override
	public Path context() {
		return null;
	}

}
