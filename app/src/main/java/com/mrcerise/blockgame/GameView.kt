package com.mrcerise.blockgame

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.SystemClock
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsets
import kotlin.math.abs
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    // ---------------- game state ----------------
    private val prefs: android.content.SharedPreferences =
        context.getSharedPreferences("blockblast_save", Context.MODE_PRIVATE)
    val sound = SoundManager()
    val game = Game()

    // ---------------- metrics ----------------
    private val density = resources.displayMetrics.density
    fun dp(v: Float) = v * density

    // board geometry
    private var boardLeft = 0f
    private var boardTop = 0f
    private var boardSize = 0f
    private var cell = 0f
    private var gap = 0f
    private var boardPad = 0f

    // tray geometry
    private var trayCell = 0f
    private var trayGap = 0f
    private var trayCenterY = 0f
    private val slotCenterX = FloatArray(3)

    // ui hit rects
    private val gearRect = RectF()
    private val btnPlayAgain = RectF()
    private val btnRestart = RectF()
    private val switchRect = RectF()
    private val closeRect = RectF()

    // ---------------- drag state ----------------
    private var dragSlot = -1
    private var dragShape: PieceShape? = null
    private var dragX = 0f
    private var dragY = 0f
    private var downX = 0f
    private var downY = 0f
    private var anchorRow = -1
    private var anchorCol = -1
    private var anchorValid = false
    private var dragOverBoard = false

    // ---------------- animations ----------------
    private class ClearAnim(val cells: List<Cell>, val start: Long)
    private class PopAnim(val cells: List<Cell>, val start: Long)
    private class Floater(val text: String, val x: Float, val y: Float, val big: Boolean, val gold: Boolean, val start: Long)

    private var clearAnim: ClearAnim? = null
    private var popAnim: PopAnim? = null
    private val floaters = ArrayList<Floater>()
    private val trayBorn = LongArray(3)
    private var shakeStart = -1L
    private var gameOverAt = -1L

    private var showSettings = false
    private var showGameOver = false

    // ---------------- paints ----------------
    private val pBlock = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pFill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        isAntiAlias = true
    }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val pTextLeft = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
    }

    private val fontBold = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    private val fontNormal = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

    init {
        sound.enabled = prefs.getBoolean("sound_on", true)
        game.best = prefs.getInt("best", 0)
        loadGame()
        if (game.tray.all { it == null }) game.refillTray()
        val now = SystemClock.uptimeMillis()
        for (i in 0..2) {
            if (game.tray[i] != null && trayBorn[i] == 0L) trayBorn[i] = now + i * 90L
        }
        if (game.over) {
            showGameOver = true
        }
        setOnApplyWindowInsetsListener { _, insets ->
            val (top, bottom) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                bars.top to bars.bottom
            } else {
                @Suppress("DEPRECATION")
                insets.systemWindowInsetTop to insets.systemWindowInsetBottom
            }
            setPadding(0, top, 0, bottom)
            insets
        }
    }

    // ============================================================ layout
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val W = w.toFloat()
        val contentTop = paddingTop.toFloat()
        val contentBottom = h - paddingBottom.toFloat()
        val contentH = contentBottom - contentTop

        val sideMargin = dp(10f)
        // board sized by both width and the reference's ~41%-of-height proportion
        boardSize = minOf(W - sideMargin * 2f, contentH * 0.46f)
        cell = boardSize / 9.14f
        gap = cell * 0.06f
        boardPad = cell * 0.36f
        boardLeft = (W - boardSize) / 2f
        boardTop = contentTop + contentH * 0.195f

        trayCell = cell * 0.5f
        trayGap = trayCell * 0.08f
        trayCenterY = contentTop + contentH * 0.78f
        for (i in 0..2) slotCenterX[i] = boardLeft + boardSize * (i + 0.5f) / 3f
    }

    private fun cellOrigin(row: Int, col: Int): Pair<Float, Float> =
        Pair(boardLeft + boardPad + col * (cell + gap), boardTop + boardPad + row * (cell + gap))

    private fun trayPieceOrigin(shape: PieceShape, slot: Int): Pair<Float, Float> {
        val w = shape.cols * trayCell + (shape.cols - 1) * trayGap
        val h = shape.rows * trayCell + (shape.rows - 1) * trayGap
        return Pair(slotCenterX[slot] - w / 2f, trayCenterY - h / 2f)
    }

    // ============================================================ draw
    override fun onDraw(canvas: Canvas) {
        val now = SystemClock.uptimeMillis()
        drawBackground(canvas)
        drawTopBar(canvas)
        drawScore(canvas)

        // shake
        var shakeX = 0f
        if (shakeStart > 0) {
            val t = (now - shakeStart) / 400f
            if (t < 1f) shakeX = sin(t * 28f) * dp(8f) * (1f - t) else shakeStart = -1
        }
        canvas.save()
        canvas.translate(shakeX, 0f)
        drawBoard(canvas, now)
        drawTray(canvas, now)
        canvas.restore()

        drawDrag(canvas)
        drawFloaters(canvas, now)

        // advance clear animation
        val ca = clearAnim
        if (ca != null && now - ca.start > 420) {
            clearAnim = null
            val wasEmpty = game.tray.all { it == null }
            game.afterClearAnim()
            if (wasEmpty) {
                for (i in 0..2) trayBorn[i] = now + 120 + i * 100L
            }
            saveGame()
            if (game.over && gameOverAt < 0) {
                gameOverAt = now + 500
            }
        }
        if (popAnim != null && now - popAnim!!.start > 260) popAnim = null

        if (gameOverAt in 0..now && !showGameOver) {
            showGameOver = true
            sound.gameOver()
            haptic(HapticFeedbackConstants.LONG_PRESS)
        }

        if (showSettings) drawSettings(canvas)
        if (showGameOver) drawGameOver(canvas)

        scheduleNextFrame(now)
    }

    private fun scheduleNextFrame(now: Long) {
        val busy = dragSlot >= 0 || clearAnim != null || popAnim != null ||
            floaters.isNotEmpty() || shakeStart > 0 || gameOverAt > now ||
            trayBorn.any { it > now - 400 }
        if (busy) postInvalidateOnAnimation()
    }

    private fun drawBackground(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        pFill.shader = LinearGradient(0f, 0f, 0f, h,
            Color.rgb(0xA8, 0x6A, 0x3D), Color.rgb(0x8B, 0x4D, 0x2A), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, pFill)
        pFill.shader = RadialGradient(w / 2f, h * 0.32f, h * 0.85f,
            intArrayOf(0x00000000, 0x22000000), floatArrayOf(0.55f, 1.0f), Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, w, h, pFill)
        pFill.shader = null
    }

    private fun drawTopBar(canvas: Canvas) {
        val y = paddingTop + dp(30f)
        // crown + best
        val crownSize = dp(26f)
        val crownX = dp(18f)
        drawCrown(canvas, crownX + crownSize / 2f, y, crownSize)
        pTextLeft.textSize = dp(19f)
        pTextLeft.typeface = fontBold
        pTextLeft.color = Color.rgb(0xF9, 0xCB, 0x6E)
        pTextLeft.setShadowLayer(dp(2f), 0f, dp(1.5f), 0x55000000)
        canvas.drawText(game.best.toString(), crownX + crownSize + dp(8f), y + dp(7f), pTextLeft)
        pTextLeft.clearShadowLayer()

        // gear + NEW badge (fully inside the right edge; the pill sits on the gear's upper-right)
        val gearR = dp(20f)
        val gx = width - dp(38f)
        gearRect.set(gx - gearR, y - gearR, gx + gearR, y + gearR)
        drawGear(canvas, gx, y, gearR)
        // NEW pill
        val pillW = dp(44f); val pillH = dp(20f)
        val px = gx + gearR * 0.18f; val py = y - gearR * 1.18f
        pFill.shader = LinearGradient(px, py, px, py + pillH,
            Color.rgb(0xF7, 0xB4, 0x3C), Color.rgb(0xE8, 0x8E, 0x1E), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(RectF(px, py, px + pillW, py + pillH), pillH / 2f, pillH / 2f, pFill)
        pFill.shader = null
        pText.textSize = dp(11f)
        pText.typeface = fontBold
        pText.color = Color.WHITE
        canvas.drawText("NEW", px + pillW / 2f, py + pillH * 0.74f, pText)
    }

    private fun drawScore(canvas: Canvas) {
        pText.textSize = cell * 1.05f
        pText.typeface = fontBold
        pText.color = Color.rgb(0xFF, 0xF6, 0xEA)
        pText.setShadowLayer(dp(3f), 0f, dp(2.5f), 0x66000000)
        val y = boardTop - dp(20f)
        canvas.drawText(game.score.toString(), width / 2f, y, pText)
        pText.clearShadowLayer()
    }

    private fun drawBoard(canvas: Canvas, now: Long) {
        // frame shadow + frame
        pFill.color = 0x55000000
        canvas.drawRoundRect(
            RectF(boardLeft, boardTop + dp(5f), boardLeft + boardSize, boardTop + boardSize + dp(5f)),
            dp(12f), dp(12f), pFill
        )
        pFill.color = Color.rgb(0x3A, 0x1E, 0x10)
        canvas.drawRoundRect(RectF(boardLeft, boardTop, boardLeft + boardSize, boardTop + boardSize),
            dp(12f), dp(12f), pFill)

        val clearing = clearAnim?.cells ?: emptyList()
        val tClear = clearAnim?.let { (now - it.start).toFloat() } ?: 0f

        // empty cells underneath everything
        for (r in 0..7) {
            for (c in 0..7) {
                val (x, y) = cellOrigin(r, c)
                if (!game.grid[r][c] && clearing.none { it.row == r && it.col == c }) {
                    pFill.color = Color.rgb(0x4C, 0x2B, 0x1A)
                    canvas.drawRoundRect(RectF(x, y, x + cell, y + cell), cell * 0.14f, cell * 0.14f, pFill)
                }
            }
        }

        // blocks currently on the board (with pop-in for the just-placed ones)
        for (r in 0..7) {
            for (c in 0..7) {
                if (!game.grid[r][c]) continue
                val (x, y) = cellOrigin(r, c)
                val pop = popAnim?.cells?.any { it.row == r && it.col == c } == true
                if (pop) {
                    val pt = ((now - popAnim!!.start) / 230f).coerceIn(0f, 1f)
                    val s = popScale(pt)
                    val d = cell * (1f - s) / 2f
                    drawBlock(canvas, x + d, y + d, cell * s, 0, 255)
                } else {
                    drawBlock(canvas, x, y, cell, 0, 255)
                }
            }
        }

        // cells being cleared: flash white, then shrink away
        for (cc in clearing) {
            val (x, y) = cellOrigin(cc.row, cc.col)
            if (tClear < 150f) {
                val a = if (tClear < 75f) tClear / 75f else 1f - (tClear - 75f) / 75f
                drawBlock(canvas, x, y, cell, 0, (255 * (0.45f + 0.55f * a)).toInt().coerceIn(0, 255))
                pFill.color = Color.argb((a * 170).toInt().coerceIn(0, 255), 0xFF, 0xFF, 0xFF)
                canvas.drawRoundRect(RectF(x, y, x + cell, y + cell), cell * 0.14f, cell * 0.14f, pFill)
            } else {
                val s = (1f - (tClear - 150f) / 270f).coerceIn(0f, 1f)
                val d = cell * (1f - s) / 2f
                drawBlock(canvas, x + d, y + d, cell * s, 0, 255)
            }
        }

        // hover targets
        val shape = dragShape
        if (dragSlot >= 0 && shape != null && anchorRow >= 0) {
            val style = if (anchorValid) 1 else 2
            for (cc in shape.cells) {
                val r = anchorRow + cc.row
                val c = anchorCol + cc.col
                if (r in 0..7 && c in 0..7) {
                    val (x, y) = cellOrigin(r, c)
                    drawBlock(canvas, x, y, cell, style, 255)
                }
            }
        }
    }

    private fun popScale(t: Float): Float =
        if (t < 0.7f) {
            val u = t / 0.7f
            0.55f + 0.55f * (1f - (1f - u) * (1f - u))
        } else {
            val u = (t - 0.7f) / 0.3f
            1.1f - 0.1f * u
        }

    private fun drawTray(canvas: Canvas, now: Long) {
        for (i in 0..2) {
            val shape = game.tray[i] ?: continue
            if (dragSlot == i) continue
            val (ox, oy) = trayPieceOrigin(shape, i)
            val age = (now - trayBorn[i]).coerceAtLeast(0)
            val appear = (age / 320f).coerceIn(0f, 1f)
            val scale = if (appear < 1f) popScale(appear) else 1f
            val slide = (1f - appear) * dp(18f)
            val alpha = (appear * 255).toInt()
            val fits = game.canPlaceAnywhere(shape)
            canvas.save()
            val cx = slotCenterX[i]
            val cy = oy + (shape.rows * trayCell + (shape.rows - 1) * trayGap) / 2f
            canvas.translate(cx, cy + slide)
            canvas.scale(scale, scale)
            canvas.translate(-cx, -cy)
            for (cc in shape.cells) {
                val x = ox + cc.col * (trayCell + trayGap)
                val y = oy + cc.row * (trayCell + trayGap)
                drawBlock(canvas, x, y, trayCell, if (fits) 0 else 3, alpha)
            }
            canvas.restore()
        }
    }

    /** Top-left (in board/cell metrics) of the piece as it floats above the finger. */
    private fun boardDragOrigin(shape: PieceShape): Pair<Float, Float> {
        val w = shape.cols * cell + (shape.cols - 1) * gap
        val lift = cell * 1.15f
        val ox = dragX - w / 2f
        val oy = dragY - lift - shape.rows * (cell + gap)
        return Pair(ox, oy)
    }

    private fun drawDrag(canvas: Canvas) {
        val shape = dragShape ?: return
        if (dragSlot < 0) return
        if (dragOverBoard) {
            val (ox, oy) = boardDragOrigin(shape)
            for (cc in shape.cells) {
                drawBlock(canvas, ox + cc.col * (cell + gap), oy + cc.row * (cell + gap), cell, 0, 235)
            }
        } else {
            val w = shape.cols * trayCell + (shape.cols - 1) * trayGap
            val lift = trayCell * 1.3f
            val ox = dragX - w / 2f
            val oy = dragY - lift - shape.rows * (trayCell + trayGap)
            for (cc in shape.cells) {
                drawBlock(canvas, ox + cc.col * (trayCell + trayGap), oy + cc.row * (trayCell + trayGap), trayCell, 0, 235)
            }
        }
    }

    private fun drawFloaters(canvas: Canvas, now: Long) {
        val it = floaters.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val t = (now - f.start) / 900f
            if (t >= 1f) { it.remove(); continue }
            val alpha = (if (t < 0.15f) t / 0.15f else 1f - (t - 0.15f) / 0.85f).coerceIn(0f, 1f)
            val rise = t * dp(34f)
            pText.textSize = if (f.big) cell * 0.62f else cell * 0.42f
            pText.typeface = fontBold
            pText.color = if (f.gold) Color.argb((alpha * 255).toInt(), 0xFF, 0xD3, 0x7A)
            else Color.argb((alpha * 255).toInt(), 0xFF, 0xF6, 0xEA)
            pText.setShadowLayer(dp(2f), 0f, dp(1.5f), 0x66000000)
            canvas.drawText(f.text, f.x, f.y - rise, pText)
            pText.clearShadowLayer()
        }
    }

    /** style: 0 = gold block, 1 = ghost (valid), 2 = invalid, 3 = dimmed gray */
    private fun drawBlock(canvas: Canvas, x: Float, y: Float, s: Float, style: Int, alpha: Int) {
        if (s <= 0.5f) return
        val radius = s * 0.15f
        val rect = RectF(x, y, x + s, y + s)
        when (style) {
            0 -> {
                pBlock.shader = LinearGradient(x, y, x, y + s,
                    Color.rgb(0xF2, 0xCE, 0x80), Color.rgb(0xD8, 0xA6, 0x53), Shader.TileMode.CLAMP)
                pBlock.alpha = alpha
                canvas.drawRoundRect(rect, radius, radius, pBlock)
                pBlock.shader = LinearGradient(x, y + s, x, y + s * 1.06f,
                    Color.rgb(0xA8, 0x72, 0x2E), Color.rgb(0xA8, 0x72, 0x2E), Shader.TileMode.CLAMP)
                pBlock.alpha = (alpha * 0.9f).toInt()
                canvas.drawRoundRect(RectF(x, y + s, x + s, y + s * 1.06f), radius * 0.5f, radius * 0.5f, pBlock)
                pBlock.shader = null
                pBlock.alpha = 255
                // gloss
                val inset = s * 0.13f
                pFill.shader = LinearGradient(x, y, x, y + s * 0.5f,
                    Color.argb((alpha * 0.34f).toInt(), 255, 255, 255), Color.argb(0, 255, 255, 255),
                    Shader.TileMode.CLAMP)
                canvas.drawRoundRect(
                    RectF(x + inset, y + inset * 0.9f, x + s - inset, y + s * 0.48f),
                    radius * 0.7f, radius * 0.7f, pFill)
                pFill.shader = null
            }
            1 -> {
                pFill.color = Color.argb((alpha * 0.28f).toInt(), 0xF2, 0xCE, 0x86)
                canvas.drawRoundRect(rect, radius, radius, pFill)
                pStroke.color = Color.argb((alpha * 0.75f).toInt(), 0xF8, 0xD7, 0x8A)
                pStroke.strokeWidth = s * 0.045f
                canvas.drawRoundRect(rect, radius, radius, pStroke)
            }
            2 -> {
                pFill.color = Color.argb((alpha * 0.30f).toInt(), 0xDE, 0x55, 0x3A)
                canvas.drawRoundRect(rect, radius, radius, pFill)
                pStroke.color = Color.argb((alpha * 0.80f).toInt(), 0xF0, 0x7A, 0x55)
                pStroke.strokeWidth = s * 0.045f
                canvas.drawRoundRect(rect, radius, radius, pStroke)
            }
            3 -> {
                pBlock.shader = LinearGradient(x, y, x, y + s,
                    Color.argb(alpha, 0x9B, 0x8B, 0x76), Color.argb(alpha, 0x66, 0x59, 0x48),
                    Shader.TileMode.CLAMP)
                canvas.drawRoundRect(rect, radius, radius, pBlock)
                pBlock.shader = null
            }
        }
    }

    // ---------------- icons ----------------
    private fun drawCrown(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val w = size; val h = size * 0.78f
        val l = cx - w / 2f; val t = cy - h / 2f
        val path = Path()
        path.moveTo(l, t + h * 0.85f)
        path.lineTo(l, t + h * 0.25f)
        path.lineTo(l + w * 0.28f, t + h * 0.55f)
        path.lineTo(cx, t)
        path.lineTo(l + w * 0.72f, t + h * 0.55f)
        path.lineTo(l + w, t + h * 0.25f)
        path.lineTo(l + w, t + h * 0.85f)
        path.close()
        pFill.shader = LinearGradient(0f, t, 0f, t + h,
            Color.rgb(0xFF, 0xD9, 0x4D), Color.rgb(0xE8, 0xA9, 0x21), Shader.TileMode.CLAMP)
        canvas.drawPath(path, pFill)
        pFill.shader = null
        pFill.color = Color.rgb(0xF5, 0xB9, 0x2C)
        canvas.drawRect(l, t + h * 0.82f, l + w, t + h, pFill)
        pFill.color = Color.rgb(0xFF, 0xE9, 0x8A)
        canvas.drawCircle(l, t + h * 0.22f, w * 0.07f, pFill)
        canvas.drawCircle(cx, t, w * 0.07f, pFill)
        canvas.drawCircle(l + w, t + h * 0.22f, w * 0.07f, pFill)
    }

    private fun drawGear(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val teeth = 8
        val path = Path()
        for (i in 0 until teeth * 2) {
            val rr = if (i % 2 == 0) r else r * 0.82f
            val ang = Math.PI * i / teeth
            val px = cx + (rr * Math.cos(ang)).toFloat()
            val py = cy + (rr * Math.sin(ang)).toFloat()
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        path.close()
        pFill.shader = LinearGradient(0f, cy - r, 0f, cy + r,
            Color.rgb(0xE3, 0xB2, 0x7A), Color.rgb(0xC2, 0x8A, 0x52), Shader.TileMode.CLAMP)
        canvas.drawPath(path, pFill)
        pFill.shader = null
        pFill.color = Color.rgb(0x9A, 0x58, 0x38)
        canvas.drawCircle(cx, cy, r * 0.36f, pFill)
    }

    // ---------------- overlays ----------------
    private fun dimBackground(canvas: Canvas) {
        pFill.color = Color.argb(0x88, 0x2A, 0x14, 0x08)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), pFill)
    }

    private fun drawCard(canvas: Canvas, rect: RectF) {
        pFill.color = 0x66000000
        canvas.drawRoundRect(RectF(rect.left, rect.top + dp(6f), rect.right, rect.bottom + dp(6f)),
            dp(18f), dp(18f), pFill)
        pFill.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
            Color.rgb(0x7E, 0x48, 0x28), Color.rgb(0x5C, 0x31, 0x19), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), pFill)
        pFill.shader = null
        pStroke.color = Color.argb(160, 0xE0, 0xA8, 0x62)
        pStroke.strokeWidth = dp(1.5f)
        canvas.drawRoundRect(rect, dp(18f), dp(18f), pStroke)
    }

    private fun drawGameOver(canvas: Canvas) {
        dimBackground(canvas)
        val cw = boardSize * 0.82f
        val ch = dp(330f)
        val cx = width / 2f
        val cy = boardTop + boardSize / 2f
        val card = RectF(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
        drawCard(canvas, card)

        pText.textSize = dp(26f)
        pText.typeface = fontBold
        pText.color = Color.rgb(0xFF, 0xF6, 0xEA)
        canvas.drawText("GAME OVER", cx, card.top + dp(46f), pText)

        drawCrown(canvas, cx, card.top + dp(88f), dp(30f))
        pText.textSize = dp(16f)
        pText.typeface = fontBold
        pText.color = Color.rgb(0xF9, 0xCB, 0x6E)
        canvas.drawText("BEST  " + game.best, cx, card.top + dp(118f), pText)

        pText.textSize = dp(13f)
        pText.typeface = fontNormal
        pText.color = Color.argb(200, 0xFF, 0xE8, 0xCE)
        canvas.drawText("SCORE", cx, card.top + dp(158f), pText)
        pText.textSize = dp(40f)
        pText.typeface = fontBold
        pText.color = Color.WHITE
        canvas.drawText(game.score.toString(), cx, card.top + dp(200f), pText)

        val bw = cw * 0.7f; val bh = dp(52f)
        btnPlayAgain.set(cx - bw / 2f, card.bottom - dp(74f), cx + bw / 2f, card.bottom - dp(74f) + bh)
        pFill.shader = LinearGradient(0f, btnPlayAgain.top, 0f, btnPlayAgain.bottom,
            Color.rgb(0xF8, 0xD7, 0x8A), Color.rgb(0xDF, 0xAF, 0x5E), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(btnPlayAgain, bh / 2f, bh / 2f, pFill)
        pFill.shader = null
        pText.textSize = dp(18f)
        pText.typeface = fontBold
        pText.color = Color.rgb(0x5A, 0x32, 0x16)
        canvas.drawText("PLAY AGAIN", cx, btnPlayAgain.centerY() + dp(7f), pText)
    }

    private fun drawSettings(canvas: Canvas) {
        dimBackground(canvas)
        val cw = boardSize * 0.8f
        val ch = dp(250f)
        val cx = width / 2f
        val cy = boardTop + boardSize / 2f
        val card = RectF(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f)
        drawCard(canvas, card)

        pText.textSize = dp(23f)
        pText.typeface = fontBold
        pText.color = Color.rgb(0xFF, 0xF6, 0xEA)
        canvas.drawText("SETTINGS", cx, card.top + dp(44f), pText)

        // close X
        closeRect.set(card.right - dp(42f), card.top + dp(12f), card.right - dp(10f), card.top + dp(44f))
        pText.textSize = dp(22f)
        pText.color = Color.rgb(0xFF, 0xE8, 0xCE)
        canvas.drawText("×", closeRect.centerX(), closeRect.centerY() + dp(8f), pText)

        // sound row
        val rowY = card.top + dp(92f)
        pText.textAlign = Paint.Align.LEFT
        pText.textSize = dp(18f)
        pText.typeface = fontNormal
        pText.color = Color.rgb(0xFF, 0xF0, 0xDE)
        canvas.drawText("Sound", card.left + dp(28f), rowY + dp(7f), pText)
        pText.textAlign = Paint.Align.CENTER

        val sw = dp(54f); val shh = dp(30f)
        switchRect.set(card.right - dp(28f) - sw, rowY - shh / 2f, card.right - dp(28f), rowY + shh / 2f)
        pFill.color = if (sound.enabled) Color.rgb(0xF0, 0xB7, 0x5E) else Color.rgb(0x6B, 0x47, 0x30)
        canvas.drawRoundRect(switchRect, shh / 2f, shh / 2f, pFill)
        val knobX = if (sound.enabled) switchRect.right - shh / 2f else switchRect.left + shh / 2f
        pFill.color = Color.WHITE
        canvas.drawCircle(knobX, switchRect.centerY(), shh / 2f - dp(3f), pFill)

        // restart button
        val bw = cw * 0.66f; val bh = dp(48f)
        btnRestart.set(cx - bw / 2f, card.bottom - dp(70f), cx + bw / 2f, card.bottom - dp(70f) + bh)
        pFill.color = Color.argb(0, 0, 0, 0)
        pStroke.color = Color.rgb(0xF0, 0xB7, 0x5E)
        pStroke.strokeWidth = dp(2f)
        canvas.drawRoundRect(btnRestart, bh / 2f, bh / 2f, pStroke)
        pText.textSize = dp(17f)
        pText.typeface = fontBold
        pText.color = Color.rgb(0xF8, 0xD7, 0x8A)
        canvas.drawText("RESTART GAME", cx, btnRestart.centerY() + dp(6f), pText)
    }

    // ============================================================ touch
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x; val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = x; downY = y
                if (showGameOver) return true
                if (showSettings) return true
                if (gearRect.contains(x, y)) {
                    showSettings = true
                    sound.click()
                    invalidate()
                    return true
                }
                // tray hit test (whole slot is a generous touch target)
                val slot = slotAt(x, y)
                if (slot >= 0 && game.tray[slot] != null) {
                    dragSlot = slot
                    dragShape = game.tray[slot]
                    dragX = x; dragY = y
                    anchorRow = -1; anchorCol = -1
                    sound.pickup()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (dragSlot >= 0) {
                    dragX = x; dragY = y
                    updateAnchor()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (showGameOver) {
                    if (btnPlayAgain.contains(x, y)) { sound.click(); restart() }
                    // only the PLAY AGAIN button dismisses the game-over screen
                    invalidate()
                    return true
                }
                if (showSettings) {
                    if (closeRect.contains(x, y) || !pointInCard(x, y, true)) {
                        showSettings = false
                    } else if (switchRect.contains(x, y)) {
                        sound.enabled = !sound.enabled
                        prefs.edit().putBoolean("sound_on", sound.enabled).apply()
                        sound.click()
                    } else if (btnRestart.contains(x, y)) {
                        sound.click()
                        showSettings = false
                        restart()
                    }
                    invalidate()
                    return true
                }
                if (dragSlot >= 0) {
                    val shape = dragShape
                    val ok = shape != null && anchorValid && anchorRow >= 0
                    if (ok) {
                        placePiece(anchorRow, anchorCol)
                    } else {
                        val wasOverBoard = dragOverBoard
                        dragSlot = -1; dragShape = null; anchorRow = -1; dragOverBoard = false
                        if (wasOverBoard) {
                            sound.invalid()
                            shakeStart = SystemClock.uptimeMillis()
                            haptic(HapticFeedbackConstants.LONG_PRESS)
                        }
                    }
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragSlot = -1; dragShape = null; anchorRow = -1; dragOverBoard = false
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun pointInCard(x: Float, y: Float, settings: Boolean): Boolean {
        // rough card bounds mirror draw methods
        val cw = boardSize * if (settings) 0.8f else 0.82f
        val ch = if (settings) dp(250f) else dp(330f)
        val cx = width / 2f; val cy = boardTop + boardSize / 2f
        return x in (cx - cw / 2f)..(cx + cw / 2f) && y in (cy - ch / 2f)..(cy + ch / 2f)
    }

    private fun slotAt(x: Float, y: Float): Int {
        val trayH = trayCell * 5f
        if (y < trayCenterY - trayH / 2f - dp(24f) || y > trayCenterY + trayH / 2f + dp(24f)) return -1
        for (i in 0..2) {
            if (abs(x - slotCenterX[i]) < boardSize / 6f) return i
        }
        return -1
    }

    private fun updateAnchor() {
        val shape = dragShape ?: return
        anchorValid = false
        anchorRow = -1; anchorCol = -1
        dragOverBoard = false

        // decide board-mode by whether the floating piece (lifted above the finger)
        // overlaps the board — this lets pieces reach the bottom row even though the
        // finger itself drops below the board frame
        val (ox, oy) = boardDragOrigin(shape)
        val pieceW = shape.cols * cell + (shape.cols - 1) * gap
        val pieceH = shape.rows * cell + (shape.rows - 1) * gap
        val m = cell
        val overlaps = oy + pieceH >= boardTop - m && oy <= boardTop + boardSize + m &&
            ox + pieceW >= boardLeft - m && ox <= boardLeft + boardSize + m
        if (!overlaps) return
        dragOverBoard = true

        // anchor row/col = where the piece's top-left would snap on the grid
        val r = Math.round((oy - (boardTop + boardPad)) / (cell + gap))
        val c = Math.round((ox - (boardLeft + boardPad)) / (cell + gap))
        anchorRow = r
        anchorCol = c
        anchorValid = game.canPlace(shape, r, c)
    }

    // ============================================================ actions
    private fun placePiece(r: Int, c: Int) {
        val slot = dragSlot
        val shape = dragShape ?: return
        val before = game.tray.map { it != null }
        val result = game.place(slot, r, c)
        if (!result.placed) {
            dragSlot = -1; dragShape = null
            return
        }
        val now = SystemClock.uptimeMillis()
        dragSlot = -1; dragShape = null; anchorRow = -1; anchorCol = -1

        val placed = shape.cells.map { Cell(r + it.row, c + it.col) }
        popAnim = PopAnim(placed, now)
        sound.place()
        haptic(HapticFeedbackConstants.KEYBOARD_TAP)

        if (result.clearedLines > 0) {
            clearAnim = ClearAnim(result.clearedCells, now + 150)
            sound.clear(result.clearedLines)
            haptic(HapticFeedbackConstants.LONG_PRESS)
            val centerX = width / 2f
            val centerY = boardTop + boardSize / 2f
            floaters.add(Floater("+${result.gained}", centerX, centerY - cell * 0.6f, false, false, now + 200))
            val praise = when (result.clearedLines) {
                2 -> "GOOD!"
                3 -> "GREAT!"
                4 -> "EXCELLENT!"
                else -> "UNBELIEVABLE!"
            }
            floaters.add(Floater(praise, centerX, centerY + cell * 0.2f, true, true, now + 200))
            if (result.streak >= 2) {
                floaters.add(Floater("COMBO x${result.streak}", centerX, centerY + cell * 0.95f, false, true, now + 200))
            }
        }
        // refilled slots animate in
        for (i in 0..2) {
            if (!before[i] && game.tray[i] != null) trayBorn[i] = now + 250 + i * 90L
        }
        saveGame()
        if (game.over && clearAnim == null && gameOverAt < 0) {
            gameOverAt = now + 500
        }
    }

    private fun restart() {
        game.reset()
        clearAnim = null; popAnim = null
        floaters.clear()
        gameOverAt = -1L
        showGameOver = false
        showSettings = false
        shakeStart = -1
        val now = SystemClock.uptimeMillis()
        for (i in 0..2) trayBorn[i] = now + i * 90L
        saveGame()
        invalidate()
    }

    private fun haptic(constant: Int) {
        try {
            performHapticFeedback(constant, android.view.HapticFeedbackConstants.FLAG_IGNORE_VIEW_SETTING)
        } catch (_: Throwable) {
            try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Throwable) {}
        }
    }

    // ============================================================ persistence
    private fun saveGame() {
        prefs.edit()
            .putInt("best", game.best)
            .putInt("score", game.score)
            .putString("grid", game.serializeGrid())
            .putString("tray", game.serializeTray())
            .putBoolean("over", game.over)
            .apply()
    }

    private fun loadGame() {
        if (!prefs.contains("grid")) return
        try {
            game.score = prefs.getInt("score", 0)
            game.deserializeGrid(prefs.getString("grid", "") ?: "")
            game.deserializeTray(prefs.getString("tray", "-1,-1,-1") ?: "-1,-1,-1")
            // existing pieces shouldn't play the entrance animation
            val old = SystemClock.uptimeMillis() - 10000L
            for (i in 0..2) if (game.tray[i] != null) trayBorn[i] = old
            if (game.over) {
                // leave the saved game-over state as-is
                return
            }
            game.best = maxOf(game.best, game.score)
        } catch (_: Throwable) {
        }
    }
}
