
import android.content.Context
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import kotlin.math.roundToInt

/**
 * Created by janpawlov ( ͡° ͜ʖ ͡°) on 31/01/2018.
 */
class ChooseWorkoutLayout(context: Context, attributeSet: AttributeSet) : LinearLayout(context, attributeSet) {

    private var mMargin: Int = 0
    private var circleSize: Int = 0

    private var mLeftInvisibleCircle: WorkoutCircle
    private var mRightInvisibleCircle: WorkoutCircle
    private var mLeftInvisibleLine: View
    private var mRightInvisibleLine: View
    private var mLeftLine: View
    private var mRightLine: View

    var mLeftWorkoutCircle: WorkoutCircle
    var mMiddleWorkoutCircle: WorkoutCircle
    var mRightWorkoutCircle: WorkoutCircle
    var mInitialPositionX: Float = 0f
    val mLeftPositionX: Float = 0f
    var mRightPosition: Float = 0f

    init {
        id = View.generateViewId()
        //setting WorkoutLayout params
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT)

        //initializing invisible circles
        mLeftInvisibleCircle = WorkoutCircle(context = context)
        mLeftInvisibleCircle.id = View.generateViewId()
        circleSize = mLeftInvisibleCircle.circleSize
        mMargin = circleSize / 4

        val circleParams = LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        circleParams.gravity = Gravity.CENTER_VERTICAL
        circleParams.setMargins(mMargin, 0, 0, 0)

        mLeftInvisibleCircle.layoutParams = circleParams

        //right invisible circle has to have margins on both left and right side
        val lastInvisibleCircleParams = LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lastInvisibleCircleParams.gravity = Gravity.CENTER_VERTICAL
        lastInvisibleCircleParams.setMargins(mMargin, 0, mMargin, 0)

        mRightInvisibleCircle = WorkoutCircle(context = context)
        mRightInvisibleCircle.id = View.generateViewId()
        mRightInvisibleCircle.layoutParams = lastInvisibleCircleParams
        mRightInvisibleCircle.visibility = View.INVISIBLE

        mLeftWorkoutCircle = WorkoutCircle(context = context, position = 1)
        mLeftWorkoutCircle.id = View.generateViewId()
        mLeftWorkoutCircle.layoutParams = circleParams
        mLeftWorkoutCircle.scaleCircle(1f)

        mMiddleWorkoutCircle = WorkoutCircle(context = context, position = 2)
        mMiddleWorkoutCircle.id = View.generateViewId()
        mMiddleWorkoutCircle.layoutParams = circleParams
        mMiddleWorkoutCircle.scaleCircle(MAX_SCALE_FACTOR)

        mRightWorkoutCircle = WorkoutCircle(context = context, position = 3)
        mRightWorkoutCircle.id = View.generateViewId()
        mRightWorkoutCircle.layoutParams = circleParams
        mRightWorkoutCircle.scaleCircle(1f)

        //determining breakline width
        val displayMetrics = resources.displayMetrics
        val lineWidth = (displayMetrics.widthPixels
                - ((2 + MAX_SCALE_FACTOR) * circleSize).roundToInt()
                - 6 * mMargin) / 2

        val lineParams = LayoutParams(lineWidth, LINE_HEIGHT)
        lineParams.gravity = Gravity.CENTER_VERTICAL
        lineParams.setMargins(mMargin, 0, 0, 0)

        mLeftInvisibleLine = View(context)
        mLeftInvisibleLine.id = View.generateViewId()
        mLeftInvisibleLine.layoutParams = lineParams
        mLeftInvisibleLine.visibility = View.INVISIBLE

        mRightInvisibleLine = View(context)
        mRightInvisibleLine.id = View.generateViewId()
        mRightInvisibleLine.layoutParams = lineParams
        mRightInvisibleLine.visibility = View.INVISIBLE

        mLeftLine = View(context)
        mLeftLine.id = View.generateViewId()
        mLeftLine.background = ContextCompat.getDrawable(context, R.drawable.breakline_rectangle)
        mLeftLine.layoutParams = lineParams

        mRightLine = View(context)
        mRightLine.id = View.generateViewId()
        mRightLine.background = ContextCompat.getDrawable(context, R.drawable.breakline_rectangle)
        mRightLine.layoutParams = lineParams

        //adding views in correct order
        addViews(mLeftInvisibleCircle,
                mLeftInvisibleLine,
                mLeftWorkoutCircle,
                mLeftLine,
                mMiddleWorkoutCircle,
                mRightLine,
                mRightWorkoutCircle,
                mRightInvisibleLine,
                mRightInvisibleCircle)

        mInitialPositionX = (2 * mMargin + circleSize + lineWidth).toFloat()
        mRightPosition = 2 * mInitialPositionX
    }

    companion object {
        const val MAX_SCALE_FACTOR = 2.0f
        const val LINE_HEIGHT = 3
    }
}
