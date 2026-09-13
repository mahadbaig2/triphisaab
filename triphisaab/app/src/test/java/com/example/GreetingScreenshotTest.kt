package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.data.model.Expense
import com.example.ui.screens.ExpenseListItem
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [34])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun expenseItem_screenshot() {
    val sampleExpense = Expense(
      budgetId = "b1",
      amountPaisa = 2200_00L,
      description = "Petrol"
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        ExpenseListItem(expense = sampleExpense, placeName = "Gilgit")
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/expense_item.png")
  }
}

