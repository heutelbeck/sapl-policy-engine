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
