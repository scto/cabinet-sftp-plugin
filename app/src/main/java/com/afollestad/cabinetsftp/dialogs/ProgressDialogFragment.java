package com.afollestad.cabinetsftp.dialogs;

import android.app.Dialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AppCompatActivity;

import com.afollestad.materialdialogs.MaterialDialog;

/**
 * @author Aidan Follestad (afollestad)
 */
public class ProgressDialogFragment extends DialogFragment {

    public static ProgressDialogFragment create(@StringRes int message) {
        ProgressDialogFragment frag = new ProgressDialogFragment();
        Bundle args = new Bundle();
        args.putInt("message", message);
        frag.setArguments(args);
        return frag;
    }

    public ProgressDialogFragment show(AppCompatActivity context) {
        show(context.getSupportFragmentManager(), "progress_dialog");
        return this;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setCancelable(false);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new MaterialDialog.Builder(getActivity())
                .content(getArguments().getInt("message"))
                .cancelable(false)
                .progress(true, 0)
                .build();
    }
}
