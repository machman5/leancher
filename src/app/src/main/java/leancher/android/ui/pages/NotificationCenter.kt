package leancher.android.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AmbientContext
import androidx.compose.ui.unit.dp
import leancher.android.MainActivity
import leancher.android.R
import leancher.android.domain.models.PageTitle
import leancher.android.ui.components.*
import leancher.android.ui.components.itemtemplates.NotificationItemTemplate
import leancher.android.viewmodels.NotificationCenterViewModel


@Composable
fun NotificationCenter(notificationCenterViewModel: NotificationCenterViewModel) {
    val context = AmbientContext.current
    val activity: MainActivity = context as MainActivity

    val notificationTitleModel = PageTitle(
        context.getString(leancher.android.R.string.page_notification_center),
        "Manage your notifications here",
        R.drawable.notification
    )

    Row {
        Column(Modifier.padding(10.dp)) {
            TitleCard(pageTitle = notificationTitleModel, null)
        }
    }

    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ActionSwitch(
            onAction = { activity.showOrHideStatusBar() },
            offAction = { activity.showOrHideStatusBar(false) },
            text = "Hide Notifications"
        )
        Spacer(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .background(color = MaterialTheme.colors.secondary)
                .defaultMinSizeConstraints(minHeight = 30.dp, minWidth = 2.dp)
        )
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
            IconButton(icon = Icons.Filled.Delete, action = {
                activity.clearNotifications()
            }, "Clear all")
        }
    }

    Row {
        SwipeActionList(
            innerPadding = PaddingValues(),
            items = notificationCenterViewModel.notifications.toList(),
            itemTemplate = { notification -> NotificationItemTemplate(notification) },
            onSwipe = { notification -> activity.dismissNotification(notification) },
            onClick = { notification ->
                val launchIntent = context.getPackageManager()?.getLaunchIntentForPackage(notification.packageName)
                if(launchIntent != null) {
                    context.startActivity(launchIntent)
                }
            },
            Modifier.fillMaxWidth()
        )
    }
}