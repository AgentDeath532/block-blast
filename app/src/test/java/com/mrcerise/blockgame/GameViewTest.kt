package com.mrcerise.blockgame

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class GameViewTest {

    private val W = 1080
    private val H = 2400

    private fun touch(view: View, action: Int, x: Float, y: Float) {
        val ev = MotionEvent.obtain(
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), action, x, y, 0
        )
        view.dispatchTouchEvent(ev)
        ev.recycle()
    }

    private fun GameView.field(name: String): Any? {
        val f = GameView::class.java.getDeclaredField(name)
        f.isAccessible = true
        return f.get(this)
    }

    private fun GameView.setField(name: String, value: Any?) {
        val f = GameView::class.java.getDeclaredField(name)
        f.isAccessible = true
        f.set(this, value)
    }

    @Test
    fun fullFlow_renders_places_opensSettings_andRestarts() {
        val app = RuntimeEnvironment.getApplication()
        val view = GameView(app)
        view.measure(
            View.MeasureSpec.makeMeasureSpec(W, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(H, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, W, H)

        fun f(name: String) = view.field(name) as Float
        val boardLeft = f("boardLeft"); val boardTop = f("boardTop")
        val boardPad = f("boardPad"); val cell = f("cell"); val gap = f("gap")
        val slotCenterX = view.field("slotCenterX") as FloatArray
        val trayCenterY = f("trayCenterY")

        // initial draw must not crash
        val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)

        // force a deterministic single-block piece into slot 0
        val single = PieceShape(listOf(Cell(0, 0)))
        view.game.tray[0] = single
        val slotX = slotCenterX[0]
        // target: top-left cell (empty on fresh board)
        val r = 0; val c = 0
        val originX = boardLeft + boardPad + c * (cell + gap)
        val originY = boardTop + boardPad + r * (cell + gap)
        val dropX = originX + cell / 2f
        val dropY = originY + cell * 1.15f + 1 * (cell + gap)

        assertFalse(view.game.grid[r][c])
        // drag from tray to the board
        touch(view, MotionEvent.ACTION_DOWN, slotX, trayCenterY)
        touch(view, MotionEvent.ACTION_MOVE, slotX, trayCenterY - 200f)
        touch(view, MotionEvent.ACTION_MOVE, dropX, dropY + 300f)
        touch(view, MotionEvent.ACTION_MOVE, dropX, dropY)
        touch(view, MotionEvent.ACTION_UP, dropX, dropY)

        // the block landed and scored 1 point (no line cleared)
        assertTrue("block should be placed at (0,0)", view.game.grid[r][c])
        assertEquals("single block scores 1", 1, view.game.score)
        assertTrue("slot should now be empty", view.game.tray[0] == null)

        // draw again with placed block + floating/anim state
        view.draw(canvas)
        RobolectricHelpers.idleAnimations()
        view.draw(canvas)

        // open settings via the gear
        val gearX = W - 38f * app.resources.displayMetrics.density
        val gearY = view.paddingTop + 30f * app.resources.displayMetrics.density
        touch(view, MotionEvent.ACTION_DOWN, gearX, gearY)
        assertEquals(true, view.field("showSettings"))
        view.draw(canvas)

        // toggle the sound switch
        val switchRect = view.field("switchRect") as RectF
        val soundBefore = view.sound.enabled
        touch(view, MotionEvent.ACTION_DOWN, switchRect.centerX(), switchRect.centerY())
        touch(view, MotionEvent.ACTION_UP, switchRect.centerX(), switchRect.centerY())
        assertEquals(!soundBefore, view.sound.enabled)
        touch(view, MotionEvent.ACTION_DOWN, switchRect.centerX(), switchRect.centerY())
        touch(view, MotionEvent.ACTION_UP, switchRect.centerX(), switchRect.centerY())
        assertEquals(soundBefore, view.sound.enabled)

        // close settings
        view.setField("showSettings", false)

        // show the game-over overlay and restart via PLAY AGAIN
        view.setField("showGameOver", true)
        view.game.score = 12345
        view.draw(canvas)
        val btn = view.field("btnPlayAgain") as RectF
        touch(view, MotionEvent.ACTION_DOWN, btn.centerX(), btn.centerY())
        touch(view, MotionEvent.ACTION_UP, btn.centerX(), btn.centerY())
        assertEquals(false, view.field("showGameOver"))
        assertEquals(0, view.game.score)
        view.draw(canvas)

        // invalid drop on an occupied cell shakes and keeps the piece
        view.game.grid[0][0] = true
        view.game.tray[0] = single
        touch(view, MotionEvent.ACTION_DOWN, slotX, trayCenterY)
        touch(view, MotionEvent.ACTION_MOVE, slotX, trayCenterY - 200f)
        touch(view, MotionEvent.ACTION_MOVE, dropX, dropY) // (0,0) occupied
        touch(view, MotionEvent.ACTION_UP, dropX, dropY)
        assertNotNull("piece should remain after invalid drop", view.game.tray[0])
        assertTrue("shake should trigger", (view.field("shakeStart") as Long) > 0)

        // bottom-row placement must work even though the finger drops below the frame
        view.setField("shakeStart", -1L)
        view.game.grid[7][0] = false
        view.game.grid[7][1] = false
        view.game.tray[0] = PieceShape(listOf(Cell(0, 0), Cell(0, 1)))
        val domW = 2 * cell + gap
        val domOriginX = boardLeft + boardPad + 0 * (cell + gap)
        val domOriginY = boardTop + boardPad + 7 * (cell + gap)
        val dX = domOriginX + domW / 2f
        val dY = domOriginY + cell * 1.15f + 1 * (cell + gap) // finger ~0.15 cell below frame
        touch(view, MotionEvent.ACTION_DOWN, slotX, trayCenterY)
        touch(view, MotionEvent.ACTION_MOVE, slotX, trayCenterY - 200f)
        touch(view, MotionEvent.ACTION_MOVE, dX, dY - 200f)
        touch(view, MotionEvent.ACTION_MOVE, dX, dY)
        touch(view, MotionEvent.ACTION_UP, dX, dY)
        assertTrue("bottom row cell (7,0) should be filled", view.game.grid[7][0])
        assertTrue("bottom row cell (7,1) should be filled", view.game.grid[7][1])
    }
}

/** Small helper for advancing Robolectric's time/looper. */
object RobolectricHelpers {
    fun idleAnimations() {
        try {
            val shadowLooperCls = Class.forName("org.robolectric.Shadows")
            val m = shadowLooperCls.getMethod("shadowOf", android.os.Looper::class.java)
            val looper = android.os.Looper.getMainLooper()
            val shadow = m.invoke(null, looper)
            shadow.javaClass.getMethod("idle").invoke(shadow)
        } catch (_: Throwable) {
        }
    }
}
