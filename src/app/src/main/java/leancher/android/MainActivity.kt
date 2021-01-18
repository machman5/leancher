package leancher.android

import android.Manifest
import android.content.ComponentName
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.setContent
import leancher.android.domain.services.NotificationService
import leancher.android.ui.layouts.PagerLayout
import leancher.android.ui.theme.LeancherTheme
import leancher.android.viewmodels.*


class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"
    private val ACTION_NOTIFICATION_LISTENER_SETTINGS = "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"

    private val APPWIDGET_HOST_ID = 1024
    private val REQUEST_CREATE_APPWIDGET = 5
    private val REQUEST_PICK_APPWIDGET = 9

    private lateinit var appWidgetManager: AppWidgetManager
    private lateinit var appWidgetHost: AppWidgetHost

    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var  viewModelStateManager: ViewModelStateManager
    private lateinit var mainActivityViewModel: MainActivityViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("com.Leancher", MODE_PRIVATE);

        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

        requestLeancherPermissions()

        viewModelStateManager = ViewModelStateManager(this)
        initializeViewState()

        // set default view with compose => Pager
        setContent {
            Leancher()
        }
    }

    private fun initializeViewState() {
        val viewState = viewModelStateManager.restoreViewState()
        if (viewState != null) {
            mainActivityViewModel = viewState
        } else {
            mainActivityViewModel = MainActivityViewModel(
                homeViewModel = HomeViewModel(),
                feedViewModel = FeedViewModel(
                    widgets = mutableListOf()
                ),
                notificationCenterViewModel = NotificationCenterViewModel()
            )
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

    override fun onPause() {
        super.onPause()
        viewModelStateManager.persistViewState(mainActivityViewModel)
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
        val myNotificationService: NotificationService? =
            getSystemService(NotificationService::class.java)
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
        val allNames =
            Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
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

    fun selectWidget() {
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

        mainActivityViewModel.feedViewModel.widgets.add(Widget(appWidgetId, appWidgetInfo))
    }

    fun removeWidget(widget: Widget) {
        mainActivityViewModel.feedViewModel.widgets.removeIf {
                w -> w.id == widget.id
        }
    }

    @Composable
    fun Leancher() {
        LeancherTheme(
            content = {
                PagerLayout(mainActivityViewModel = mainActivityViewModel)
            }
        )
    }

    private fun testIntentStuff() {
        fun isIntentCallable(intent: Intent): Boolean =
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY) // TODO: check whether this flag is needed
                .isNotEmpty()

        val testIntent = Intent("test")
        val sendIntent = Intent("send")
        val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:5551234"))
        val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.android.com"))

        Log.i("INTENTS", "test ?= ${isIntentCallable(testIntent)}")
        Log.i("INTENTS", "send ?= ${isIntentCallable(sendIntent)}")
        Log.i("INTENTS", "call ?= ${isIntentCallable(callIntent)}")
        Log.i("INTENTS", "web ?= ${isIntentCallable(webIntent)}")

        startActivity(Intent.createChooser(sendIntent, "Chose some app..."))
    }
}

/*

String action !including Namespace // https://developer.android.com/reference/android/content/Intent#Intent(java.lang.String)
Uri data | setDataAndNormalize // https://developer.android.com/reference/android/content/Intent#setData(android.net.Uri)
String type | setTypeAndNormalize // https://developer.android.com/reference/android/content/Intent#setTypeAndNormalize(java.lang.String)

Boolean showChooser // https://developer.android.com/training/basics/intents/sending#AppChooser
String? chooserTitle


 */