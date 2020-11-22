/*
 * Copyright © 2017-2021 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.server.ce.views.utils.error;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.Notification.Position;

import lombok.NonNull;

/**
 * Utils for showing error notifications.
 */
public final class ErrorNotificationUtils {
	private static final int NOTIFICATION_DURATION_IN_MS = 5000;
	private static final Position NOTIFICATION_POSITION = Position.TOP_CENTER;

	private ErrorNotificationUtils() {
	}

	/**
	 * Shows an error notification with a specified error message.
	 * 
	 * @param errorMessage the error message to show
	 */
	public static void show(@NonNull String errorMessage) {
		ErrorNotificationContent content = new ErrorNotificationContent(errorMessage);

		Notification notification = new Notification(content);
		notification.setDuration(NOTIFICATION_DURATION_IN_MS);
		notification.setPosition(NOTIFICATION_POSITION);
		notification.open();
	}

	/**
	 * Shows an error notification with a specified error message via an instance of
	 * {@link Throwable}.
	 * 
	 * @param throwable the {@link Throwable} to show its message
	 */
	public static void show(@NonNull Throwable throwable) {
		show(throwable.getMessage());
	}
}
