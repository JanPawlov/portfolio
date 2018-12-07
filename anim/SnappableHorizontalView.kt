
import android.content.Context
import android.os.Handler
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import kotlin.math.roundToInt

/**
 * Created by janpawlov ( ͡° ͜ʖ ͡°) on 02/02/2018.
 */
class SnappableHorizontalView(context: Context, attributeSet: AttributeSet) : HorizontalScrollView(context, attributeSet), GestureDetector.OnGestureListener {

    private val mGestureDetector: GestureDetector = GestureDetector(context, this)
    val chooseWorkoutLayout: ChooseWorkoutLayout = ChooseWorkoutLayout(context, attributeSet)

    private var mScrollListener: ScrollObserver? = null

    private val mIconAnimHandler = Handler()

    init {
        overScrollMode = View.OVER_SCROLL_NEVER //disables over-scroll indicators on sides
        addView(chooseWorkoutLayout)
        setOnTouchListener { _, event ->
            // touch listener handles case in which user lifts his finger up
            if (mGestureDetector.onTouchEvent(event))
                true
            else if (event?.action == MotionEvent.ACTION_UP || event?.action == MotionEvent.ACTION_CANCEL) {
                val firstBoundaryPosition = (chooseWorkoutLayout.mInitialPositionX * 0.5).roundToInt()
                val secondBoundaryPosition = firstBoundaryPosition * 3
                when {
                    scrollX < firstBoundaryPosition -> smoothScrollTo(chooseWorkoutLayout.mLeftPositionX.toInt(), 0)
                    scrollX in firstBoundaryPosition..(secondBoundaryPosition - 1) -> smoothScrollTo(chooseWorkoutLayout.mInitialPositionX.toInt(), 0)
                    else -> smoothScrollTo(chooseWorkoutLayout.mRightPosition.toInt(), 0)
                }
                true
            } else false
        }

        viewTreeObserver.addOnScrollChangedListener {
            // called when scroll occurred and is finished
            setScaleOnActionUp()
            mScrollListener?.onFling(scrollX, chooseWorkoutLayout.mInitialPositionX, chooseWorkoutLayout.mRightPosition)
        }
    }

    fun attachScrollListener(mScrollListener: ScrollObserver?) {
        this.mScrollListener = mScrollListener
    }

    fun getPositionY() = chooseWorkoutLayout.mMiddleWorkoutCircle.y //returns highest Y point in layout


    fun setScaleOnActionUp() {
        when (scrollX) {
            0 -> {
                chooseWorkoutLayout.mLeftWorkoutCircle.scaleCircle(2f)
                chooseWorkoutLayout.mMiddleWorkoutCircle.scaleCircle(1f)
                chooseWorkoutLayout.mRightWorkoutCircle.scaleCircle(1f)
                mIconAnimHandler.postDelayed({
                    chooseWorkoutLayout.mLeftWorkoutCircle.animateIcon {
                        mScrollListener?.animateArrow()
                    }
                }, 100)
            }
            chooseWorkoutLayout.mInitialPositionX.toInt() -> {
                chooseWorkoutLayout.mLeftWorkoutCircle.scaleCircle(1f)
                chooseWorkoutLayout.mMiddleWorkoutCircle.scaleCircle(2f)
                chooseWorkoutLayout.mRightWorkoutCircle.scaleCircle(1f)
                mIconAnimHandler.postDelayed({
                    chooseWorkoutLayout.mMiddleWorkoutCircle.animateIcon {
                        mScrollListener?.animateArrow()
                    }
                }, 100)
            }
            chooseWorkoutLayout.mRightPosition.toInt() -> {
                chooseWorkoutLayout.mLeftWorkoutCircle.scaleCircle(1f)
                chooseWorkoutLayout.mMiddleWorkoutCircle.scaleCircle(1f)
                chooseWorkoutLayout.mRightWorkoutCircle.scaleCircle(2f)
                mIconAnimHandler.postDelayed({
                    chooseWorkoutLayout.mRightWorkoutCircle.animateIcon {
                        mScrollListener?.animateArrow()
                    }
                }, 100)
            }
        }
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
        //handles Fling gestures
        if (Math.abs(vx) > MIN_SWIPE_THRESHOLD) { //right to left
            if (e1.x - e2.x > SWIPE_MIN_DISTANCE) {
                if (scrollX < chooseWorkoutLayout.mInitialPositionX)
                    smoothScrollTo(chooseWorkoutLayout.mInitialPositionX.toInt(), 0)
                else if (scrollX >= chooseWorkoutLayout.mInitialPositionX)
                    smoothScrollTo(chooseWorkoutLayout.mRightPosition.toInt(), 0)
                return true
            } else if (e2.x - e1.x > SWIPE_MIN_DISTANCE) { //left to right
                if (scrollX <= 0.5 * chooseWorkoutLayout.mInitialPositionX)
                    smoothScrollTo(chooseWorkoutLayout.mLeftPositionX.toInt(), 0)
                else if (0.5 * chooseWorkoutLayout.mInitialPositionX < scrollX && scrollX <= 1.5 * chooseWorkoutLayout.mInitialPositionX)
                    smoothScrollTo(chooseWorkoutLayout.mInitialPositionX.toInt(), 0)
                else
                    smoothScrollTo(chooseWorkoutLayout.mRightPosition.toInt(), 0)
            }
            //callback to fragment so proper images alphas are tweaked
            mScrollListener?.onFling(scrollX, chooseWorkoutLayout.mInitialPositionX, chooseWorkoutLayout.mRightPosition)
            setScaleOnActionUp()
            return true
        } else if (Math.abs(vy) > MIN_SWIPE_THRESHOLD) {
            if (e1.y - e2.y > MIN_INFO_DISTANCE)
                mScrollListener?.showWorkoutInfo()
            return true
        }
        return true
    }


    override fun onScroll(e1: MotionEvent, e2: MotionEvent, dx: Float, dy: Float): Boolean {
        //handles scaling the circles when user is scrolling with his finger
        if (Math.abs(e1.x - e2.x) > SWIPE_MIN_DISTANCE) {
            when (scrollX) {
                in 0..(chooseWorkoutLayout.mInitialPositionX.toInt() - 1) -> {
                    val scale = (scrollX / chooseWorkoutLayout.mInitialPositionX)
                    chooseWorkoutLayout.mLeftWorkoutCircle.scaleCircle(Math.max(2f - scale, 1f))
                    chooseWorkoutLayout.mMiddleWorkoutCircle.scaleCircle(Math.min(1f + scale, 2f))
                    chooseWorkoutLayout.mRightWorkoutCircle.scaleCircle(1f)
                }
                in chooseWorkoutLayout.mInitialPositionX.toInt()..chooseWorkoutLayout.mRightPosition.toInt() -> {
                    val scale = ((scrollX / chooseWorkoutLayout.mInitialPositionX) - 1)
                    chooseWorkoutLayout.mLeftWorkoutCircle.scaleCircle(1f)
                    chooseWorkoutLayout.mMiddleWorkoutCircle.scaleCircle(Math.max(2f - scale, 1f))
                    chooseWorkoutLayout.mRightWorkoutCircle.scaleCircle(Math.min(1f + scale, 2f))

                }
            }
            //callback to fragment so proper images alphas are tweaked
            mScrollListener?.onScroll(scrollX, chooseWorkoutLayout.mInitialPositionX, chooseWorkoutLayout.mRightPosition)
            return false
        } else if (e1.y - e2.y > MIN_INFO_DISTANCE) {
            mScrollListener?.showWorkoutInfo()
            return true
        }
        return true
    }

    override fun onShowPress(p0: MotionEvent?) {
        //empty
    }

    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        return true
        //empty
    }

    override fun onDown(p0: MotionEvent?): Boolean {
        return true //empty
    }

    override fun onLongPress(p0: MotionEvent?) {
        //empty
    }

    interface ScrollObserver { //necessary for fragment callback
        fun onScroll(scrollX: Int, initialPosition: Float, rightPosition: Float)
        fun onFling(scrollX: Int, initialPosition: Float, rightPosition: Float)
        fun animateArrow()
        fun showWorkoutInfo()
    }

    companion object {
        const val SWIPE_MIN_DISTANCE = 10
        const val MIN_INFO_DISTANCE = 100
        const val MIN_SWIPE_THRESHOLD = 100
    }
}
