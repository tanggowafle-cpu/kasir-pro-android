package com.kasirpro.app

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.kasirpro.app.ui.screens.CashierScreen
import com.kasirpro.app.ui.theme.MyApplicationTheme
import com.kasirpro.app.ui.viewmodel.KasirViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Kasir Pro", appName)
  }

  @Test
  fun `cashier screen render`() {
    val app = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = KasirViewModel(app)
    viewModel.populateDemoDataset()
    composeTestRule.setContent {
      MyApplicationTheme {
        CashierScreen(viewModel = viewModel)
      }
    }
    composeTestRule.waitForIdle()
  }
}
