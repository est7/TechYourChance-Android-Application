package com.techyourchance.android.screens.animations.widgets

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import kotlin.math.sqrt

class StackedCardsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val numCards = NUM_CARDS_DEFAULT
    private val cards = mutableListOf<MyCard>()

    private var cardShift: Float = 0f

    private var velocityTracker: VelocityTracker? = null
    private var firstDrag = false
    private var lastActionDownX: Float = 0f
    private var lastActionDownY: Float = 0f
    private var topCardWidth: Float = 0f
    private var topCardHeight: Float = 0f
    private var topCardTranslationX: Float = 0f
    private var topCardTranslationY: Float = 0f

    private val colors = arrayOf(
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.YELLOW,
        Color.MAGENTA
    ) // Extend this as needed

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        topCardWidth = width * 0.9f
        topCardHeight = topCardWidth * 0.6f
        cardShift = topCardHeight * 0.15f
        val topCardBottomMargin = height * 0.02f // Bottom margin is 2% of the view's height
        val topCardLeftMargin = (width - topCardWidth) / 2 // Center horizontally
        topCardTranslationX = topCardLeftMargin
        topCardTranslationY = this@StackedCardsView.height - topCardHeight.toInt() - topCardBottomMargin

        post { initCards() } // postpone until after layout is complete
    }

    fun setNumberOfCards(numCards: Int) {
        initCards()
    }

    private fun initCards() {
        cards.clear()
        for (i in 0 until numCards) {
            val cardColor = colors[i % colors.size]
            val cardView = CardView(context, cardColor = cardColor)
            cards.add(MyCard(i, cardView))
        }
        updateCards(shouldAnimate = false)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateCards(shouldAnimate: Boolean = false) {
        removeAllViews()
        
        for (i in 0 until numCards) {
            val card = cards.first { it.stackIndex == i }
            val cardView = card.view
            val cardScaleFactorForIndex = getCardScaleForIndex(i)
            val cardTranslationYForIndex = getTranslationYForIndex(i)
            val cardTranslationXForIndex = topCardTranslationX.toFloat()

            cardView.apply {
                layoutParams = LayoutParams(topCardWidth.toInt(), topCardHeight.toInt())

                if (shouldAnimate) {
                    val startScaleX = scaleX
                    val startScaleY = scaleY
                    val startTranslationX = translationX
                    val startTranslationY = translationY
                    val startRotation = rotation
                    val animator = ValueAnimator.ofFloat(0f, 1f)
                    animator.duration = SNAP_ANIMATION_DURATION_MS

                    animator.addUpdateListener {
                        val fraction = it.animatedValue as Float
                        scaleX = startScaleX + (cardScaleFactorForIndex - startScaleX) * fraction
                        scaleY = startScaleY + (cardScaleFactorForIndex - startScaleY) * fraction
                        translationX = startTranslationX + (cardTranslationXForIndex - startTranslationX) * fraction
                        translationY = startTranslationY + (cardTranslationYForIndex - startTranslationY) * fraction
                        rotation = startRotation * (1 - fraction)
                    }

                    animator.doOnEnd {
                        // TODO: remove if not needed
                    }
                    animator.start()
                } else {
                    scaleX = cardScaleFactorForIndex
                    scaleY = cardScaleFactorForIndex
                    translationY = cardTranslationYForIndex
                    translationX = cardTranslationXForIndex
                    rotation = 0f
                }
            }

            cardView.setOnTouchListener { _, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        firstDrag = true
                        lastActionDownX = event.rawX
                        lastActionDownY = event.rawY
                        velocityTracker = VelocityTracker.obtain()
                        velocityTracker?.addMovement(event)
                    }
                    MotionEvent.ACTION_MOVE -> {
                        velocityTracker?.apply {
                            addMovement(event)
                            computeCurrentVelocity(1000) // Units are in pixels per second
                        }
                        handleCardDrag(card, event, cardTranslationXForIndex, cardTranslationYForIndex)
                    }
                    MotionEvent.ACTION_UP -> {
                        animateCardBackToPosition(card, cardTranslationXForIndex, cardTranslationYForIndex)
                    }
                }
                true
            }
        }

        // First card should be added last to be on top
        cards.sortedBy { numCards - it.stackIndex }.map {
            addView(it.view)
        }

    }

    private fun handleCardDrag(card: MyCard, event: MotionEvent, originalX: Float, originalY: Float) {
        val cardView = card.view
        val dX = event.rawX - lastActionDownX
        val dY = event.rawY - lastActionDownY

        cardView.translationX = originalX + dX
        cardView.translationY = originalY + dY

        // Calculate the distance from the center of the card
        val centerX = cardView.width / 2
        val touchXRelativeToCenter = centerX - event.x
        val maxRotation = 5f // Max rotation in degrees

        // Calculate the rotation degree
        val rotation = maxRotation * (touchXRelativeToCenter / centerX)

        // Animate rotation on first touch
        if (dX != 0f && dY != 0f && firstDrag) {
            cardView.animate()
                .rotation(rotation)
                .setDuration(SNAP_ANIMATION_DURATION_MS / 4)
                .start()
            firstDrag = false
        }
    }

    private fun animateCardBackToPosition(card: MyCard, originalX: Float, originalY: Float) {
        var xVelocity = 0f
        var yVelocity = 0f

        velocityTracker?.let { vt ->
            xVelocity = vt.xVelocity
            yVelocity = vt.yVelocity
            vt.recycle()
            velocityTracker = null
        } ?: return

        val cardView = card.view

        val speed = sqrt(xVelocity * xVelocity + yVelocity * yVelocity)

        if (speed > VELOCITY_THRESHOLD) {
            transferCardToBack(card)
        } else {
            updateCards(shouldAnimate = true)
        }
    }

    private fun transferCardToBack(transferredCard: MyCard) {
        val transferredCardIndex = transferredCard.stackIndex
        cards.replaceAll { card ->
            return@replaceAll when {
                card.stackIndex < transferredCardIndex -> {
                    card
                }
                card.stackIndex == transferredCardIndex -> {
                    card.copy(stackIndex = numCards - 1)
                }
                else -> {
                    card.copy(stackIndex = card.stackIndex - 1)
                }
            }
        }
        updateCards(shouldAnimate = true)
    }

    private fun getTranslationYForIndex(numCard: Int): Float {
        return topCardTranslationY - numCard * cardShift
    }

    private fun getCardScaleForIndex(numCard: Int): Float {
        return 1 - 0.1f * numCard
    }

    companion object {
        private const val NUM_CARDS_DEFAULT = 4
        private const val SNAP_ANIMATION_DURATION_MS = 500L
        private const val VELOCITY_THRESHOLD = 1000f
    }

    private data class MyCard(val stackIndex: Int, val view: CardView)

}