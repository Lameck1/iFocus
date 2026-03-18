package com.lameck.ifocus

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lameck.ifocus.ui.FocusTestTags
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusScreenInstrumentedTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun launch_shows_primary_controls() {
        composeRule.onNodeWithTextResource(R.string.app_name).assertIsDisplayed()
        composeRule.onNodeWithTag(FocusTestTags.TIMER_DISPLAY).assertIsDisplayed()
        composeRule.onNodeWithTag(FocusTestTags.TOGGLE_BUTTON).assertIsDisplayed()
        composeRule.onNodeWithTag(FocusTestTags.RESET_BUTTON).assertIsDisplayed()
    }

    @Test
    fun selecting_modes_updates_timer_state_description() {
        composeRule.onNodeWithTag(FocusTestTags.MODE_SHORT_BREAK).performClick()
        composeRule.onNodeWithTag(FocusTestTags.TIMER_DISPLAY).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "05:00"
            )
        )

        composeRule.onNodeWithTag(FocusTestTags.MODE_LONG_BREAK).performClick()
        composeRule.onNodeWithTag(FocusTestTags.TIMER_DISPLAY).assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                "15:00"
            )
        )
    }

    @Test
    fun toggle_button_updates_accessible_label() {
        composeRule.onNodeWithTag(FocusTestTags.TOGGLE_BUTTON).performClick()
        composeRule.onNodeWithContentDescriptionResource(R.string.pause).assertIsDisplayed()

        composeRule.onNodeWithTag(FocusTestTags.TOGGLE_BUTTON).performClick()
        composeRule.onNodeWithContentDescriptionResource(R.string.play).assertIsDisplayed()
    }

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithTextResource(
        resId: Int
    ) = onNodeWithText(activity.getString(resId))

    private fun androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, *>.onNodeWithContentDescriptionResource(
        resId: Int
    ) = onNodeWithContentDescription(activity.getString(resId))
}