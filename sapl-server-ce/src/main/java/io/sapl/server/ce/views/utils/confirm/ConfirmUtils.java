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
package io.sapl.server.ce.views.utils.confirm;

import com.vaadin.flow.component.dialog.Dialog;

import io.sapl.server.ce.views.utils.confirm.CustomConfirmDialogContent.UserConfirmedListener;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Util for letting the user confirm a specific message.
 */
@UtilityClass
public class ConfirmUtils {
	/**
	 * Lets the user confirm a specific message.
	 * 
	 * @param message          the message to confirm
	 * @param confirmedHandler the handler for a confirmation
	 * @param cancelledHandler the handler for cancellation
	 */
	public static void letConfirm(@NonNull String message, Runnable confirmedHandler, Runnable cancelledHandler) {
		CustomConfirmDialogContent dialogContent = new CustomConfirmDialogContent(message);

		Dialog confirmDialog = new Dialog(dialogContent);
		confirmDialog.setWidth("600px");
		confirmDialog.setModal(true);
		confirmDialog.setCloseOnEsc(false);
		confirmDialog.setCloseOnOutsideClick(false);

		dialogContent.setUserConfirmedListener(new UserConfirmedListener() {
			@Override
			public void onConfirmationSet(boolean isConfirmed) {
				Runnable relevantRunnable = isConfirmed ? confirmedHandler : cancelledHandler;
				if (relevantRunnable != null) {
					relevantRunnable.run();
				}

				confirmDialog.close();
			}
		});

		confirmDialog.open();
	}
}
