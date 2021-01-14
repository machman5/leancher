package leancher.android

import android.Manifest
import android.content.ComponentName
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AmbientAnimationClock
import androidx.compose.ui.platform.setContent
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import leancher.android.domain.services.NotificationService
import leancher.android.ui.components.Pager
import leancher.android.ui.components.PagerState
import leancher.android.ui.components.Paginator
import leancher.android.ui.layouts.PagerLayout
import leancher.android.ui.states.FeedState
import leancher.android.ui.states.Widget
import leancher.android.ui.pages.Feed
import leancher.android.ui.pages.Home
import leancher.android.ui.pages.NotificationCenter
import leancher.android.ui.theme.LeancherTheme


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

    private val APPWIDGET_HOST_ID           = 1024
    private val REQUEST_CREATE_APPWIDGET    = 5
    private val REQUEST_PICK_APPWIDGET      = 9

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private var feedState: FeedState = FeedState(selectWidgetFun = { selectWidget() })

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("com.Leancher", MODE_PRIVATE);

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        requestLeancherPermissions()

        // set default view with compose => Pager
        setContent {
            Leancher()
        }
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onResume() {
        super.onResume()

        if (sharedPreferences.getBoolean("firstRun", true)) {
            editor = sharedPreferences.edit();
            editor.putBoolean("firstRun", false)
            editor.commit();

            requestLeancherPermissions()
        }
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    private fun requestLeancherPermissions() {
        if (checkSelfPermission(Manifest.permission.ACCESS_NOTIFICATION_POLICY) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_NOTIFICATION_POLICY), 1);
        }
        if (!isNotificationServiceEnabled()) {
            startActivity(Intent(ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    private fun getNotifications() {
        Log.i(TAG, "Waiting for MyNotificationService")
        val myNotificationService: NotificationService? = getSystemService(NotificationService::class.java)
        Log.i(TAG, "Active Notifications: [")
        if (myNotificationService != null) {
            for (notification in myNotificationService.getActiveNotifications()) {
                Log.i(TAG, "    " + notification.packageName + " / " + notification.tag)
            }
        }
        Log.i(TAG, "]")
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val pkgName = packageName
        val allNames = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (allNames != null && !allNames.isEmpty()) {
            for (name in allNames.split(":").toTypedArray()) {
                if (packageName == ComponentName.unflattenFromString(name)!!.packageName) {
                    return true
                }
            }
        }
        return false
    }

    private fun launchIntentTest() {
        val uriString = "https://stackoverflow.com/"
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(uriString)
        startActivity(intent)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == RESULT_OK) {
            if (requestCode == REQUEST_PICK_APPWIDGET) {
                configureWidget(data)
            } else if (requestCode == REQUEST_CREATE_APPWIDGET) {
                if (data != null) {
                    createWidget(data)
                }
            }
        } else if (resultCode == RESULT_CANCELED && data != null) {
            val appWidgetId = data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            if (appWidgetId != -1) {
                appWidgetHost.deleteAppWidgetId(appWidgetId)
            }
        }
    }

    private fun selectWidget() {
        val appWidgetId = appWidgetHost.allocateAppWidgetId()
        val pickIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_PICK)
        pickIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        startActivityForResult(pickIntent, REQUEST_PICK_APPWIDGET)
    }

    private fun configureWidget(data: Intent?) {
        val extras = data!!.extras
        val appWidgetId = extras!!.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (appWidgetInfo.configure != null) {
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            intent.component = appWidgetInfo.configure
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            startActivityForResult(intent, REQUEST_CREATE_APPWIDGET)
        } else {
            createWidget(data)
        }
    }

    private fun createWidget(data: Intent) {
        val extras = data.extras
        val appWidgetId = extras!!.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(appWidgetId)
        // val hostView = appWidgetHost.createView(this, appWidgetId, appWidgetInfo)
        // hostView.setAppWidget(appWidgetId, appWidgetInfo)

        feedState.widgets.add(Widget(appWidgetId, appWidgetInfo))
    }

    private fun removeWidget(widget: Widget) {
        feedState.widgets.remove(widget)
    }

    @Composable
    fun Leancher() {
        LeancherTheme(
                content = {
                    PagerLayout(feedState = feedState, launchIntentTest = { launchIntentTest() })
                }
        )
    }

}