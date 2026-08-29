package com.blockblast.game

import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class MainActivityTest {

    private fun launchAndDraw() {
        val activity = Robolectric.buildActivity(MainActivity::class.java)
            .create()
            .start()
            .resume()
            .visible()
            .get()
        assertNotNull(activity)
        val root = activity.findViewById<android.view.View>(android.R.id.content)
        assertNotNull(root)
        root.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(1080, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(2400, android.view.View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 1080, 2400)
        val bmp = android.graphics.Bitmap.createBitmap(1080, 2400, android.graphics.Bitmap.Config.ARGB_8888)
        root.draw(android.graphics.Canvas(bmp))
        activity.finish()
    }

    @Test
    @Config(sdk = [28], application = android.app.Application::class)
    fun launches_onAndroid9() = launchAndDraw()

    @Test
    @Config(sdk = [33], application = android.app.Application::class)
    fun launches_onAndroid13() = launchAndDraw()
}
