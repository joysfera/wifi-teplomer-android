package cz.pstehlik.wifiteplomer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import static cz.pstehlik.wifiteplomer.AppWidgetViewsFactory.getWidgetPrefsName;

/**
 * A setup screen that configures login/password.
 */
public class LoginActivity extends AppCompatActivity {

    // UI references.
    private EditText mLoginView;
    private EditText mPasswordView;
    private View mProgressView;
    private View mLoginFormView;
    private SeekBar mFontSize;
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        // Set up the login form.
        // Get widget ID from intent
        Intent intent = getIntent();
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        Log.d("LoginActivity", "id = " + appWidgetId);
        mLoginView = findViewById(R.id.login);
        mPasswordView = findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });
        mFontSize = findViewById(R.id.fontsize);

        SharedPreferences teplotyPrefs = getSharedPreferences(getWidgetPrefsName(appWidgetId), 0);
        Log.d("LoginActivity", String.format("Loading for id %d, prefs = %s", appWidgetId, teplotyPrefs));
        String login = teplotyPrefs.getString("login", "");
        String pwd = teplotyPrefs.getString("pwd", "");
        int fontsize = teplotyPrefs.getInt("fontsize", 0);
        if (login.isEmpty()) {
            teplotyPrefs = getSharedPreferences("TeplotyPrefs", 0);
            login = teplotyPrefs.getString("login", "");
            pwd = teplotyPrefs.getString("pwd", "");
            fontsize = teplotyPrefs.getInt("fontsize", 0);
        }
        mLoginView.setText(login);
        mPasswordView.setText(pwd);
        mFontSize.setProgress(fontsize);

        findViewById(R.id.email_sign_in_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        findViewById(R.id.select_sensors_button).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                selectSensors(view);
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }

    public void selectSensors(View view) {
        Intent intent = new Intent(this, SelectSensorActivity.class);
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        startActivity(intent);
    }
    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        // Reset errors.
        mLoginView.setError(null);
        mPasswordView.setError(null);

        // Store values at the time of the login attempt.
        String login = mLoginView.getText().toString();
        String password = mPasswordView.getText().toString();
        int fontsize = mFontSize.getProgress();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid login.
        if (TextUtils.isEmpty(login)) {
            mLoginView.setError(getString(R.string.error_field_required));
            focusView = mLoginView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // save credentials
            final SharedPreferences teplotyPrefs = getSharedPreferences(getWidgetPrefsName(appWidgetId), 0);
            Log.d("LoginActivity", String.format("Saving for id %d, prefs = %s", appWidgetId, teplotyPrefs));
            final String o_login = teplotyPrefs.getString("login", "");
            final String o_pwd = teplotyPrefs.getString("pwd", "");
            final int o_fontsize = teplotyPrefs.getInt("fontsize", 0);
            boolean finish = true;
            if (!o_login.equals(login) || !o_pwd.equals(password) || o_fontsize != fontsize) {
                SharedPreferences.Editor e = teplotyPrefs.edit();
                e.putString("login", login);
                e.putString("pwd", password);
                e.putInt("fontsize", fontsize);
                e.apply();
                // share credentials with activity
                getSharedPreferences("TeplotyPrefs", 0).edit()
                    .putString("login", login)
                    .putString("pwd", password)
                    .putInt("fontsize", fontsize)
                    .apply();

                if (!o_login.equals(login) || !o_pwd.equals(password)) {
                    // Show a progress spinner, and kick off a background task to
                    // perform the user login attempt.
                    showProgress(true);
                    AppWidgetViewsFactory.getTempData(getApplicationContext(), appWidgetId, new AppWidgetViewsFactory.TempDataCallback() {
                        @Override
                        public void onResult(String data) {
                            runOnUiThread(() -> {
                                showProgress(false);
                                if (data != null && data.startsWith("{\"cidla")) {
                                    updateWidget();
                                    finish();
                                } else {
                                    mPasswordView.setError(getString(R.string.error_incorrect_password));
                                    mPasswordView.requestFocus();
                                }
                            });
                        }

                        @Override
                        public void onError(Exception e) {
                            runOnUiThread(() -> {
                                showProgress(false);
                                mPasswordView.setError(getString(R.string.error_incorrect_password));
                                mPasswordView.requestFocus();
                            });
                        }
                    });
                    finish = false;
                } else {
                    // update widget
                    Intent intent = new Intent(getApplicationContext(), WidgetProvider.class);
                    if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                        // Update only the specific widget
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
                    } else {
                        // Update all widgets if no specific widget ID
                        intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
                        int[] ids = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(new ComponentName(getApplication(), WidgetProvider.class));
                        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
                    }
                    sendBroadcast(intent);
                }
            }
            if (finish) finish();
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

        mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            }
        });

        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void updateWidget() {
        Intent intent = new Intent(getApplicationContext(), WidgetProvider.class);
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{appWidgetId});
        } else {
            intent.setAction("android.appwidget.action.APPWIDGET_UPDATE");
            int[] ids = AppWidgetManager.getInstance(getApplication()).getAppWidgetIds(
                    new ComponentName(getApplication(), WidgetProvider.class));
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        }
        sendBroadcast(intent);
    }
}
