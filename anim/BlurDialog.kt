
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.support.annotation.DrawableRes
import android.support.v4.app.DialogFragment
import android.support.v4.app.FragmentManager
import android.support.v7.widget.RecyclerView
import android.util.TypedValue
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.RelativeLayout

/**
 * Created by janpawlov ( ͡° ͜ʖ ͡°) on 06/02/2018.
 */
class BlurDialogFragment : BlurDialogBDF(),BlurDialogV, GestureDetector.OnGestureListener {
    override var mPresenter: BlurDialogP = BlurDialogPresenter()

    private var mGoodForList: List<String>? = null
    private var mWorkoutDescription: String? = ""
    private var mWorkoutTitle: String? = ""
    private var mAdapter: WorkoutDescriptionAdapter? = null
    private val resID: Int? by lazy { arguments?.getInt(RES_ID) }
    private var mAnimator: Animator? = null
    private var animatingDismiss = false //flag used to prevent double animation start on back press
    private val mDetector = GestureDetector(context, this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(DialogFragment.STYLE_NO_TITLE, R.style.FullScreenDialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        resID?.let {
            //get proper strings, for currently chosen workout
            when (it) {
                R.drawable.strength_training -> {
                    mWorkoutTitle = context?.resources?.getString(R.string.strength_toning)
                    mWorkoutDescription = context?.resources?.getString(R.string.strength_explanation)
                    mGoodForList = context?.resources?.getStringArray(R.array.strength_is_good_for)?.asList()
                }
                R.drawable.flexibility_training -> {
                    mWorkoutTitle = context?.resources?.getString(R.string.functional_flexibility)
                    mWorkoutDescription = context?.resources?.getString(R.string.flexibility_explanation)
                    mGoodForList = context?.resources?.getStringArray(R.array.flexibility_is_good_for)?.asList()
                }
                R.drawable.circuit_training -> {
                    mWorkoutTitle = context?.resources?.getString(R.string.circuit_training)
                    mWorkoutDescription = context?.resources?.getString(R.string.circuit_explanation)
                    mGoodForList = context?.resources?.getStringArray(R.array.circuit_is_good_for)?.asList()
                }
            }
            mAdapter = WorkoutDescriptionAdapter(mGoodForList!!)
        }
        return inflater.inflate(R.layout.dialog_blur_workout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.view?.setOnKeyListener { _, keyCode, _ ->
            if (keyCode == KeyEvent.KEYCODE_BACK && !animatingDismiss) {
                animatingDismiss = true //set the flag to prevent resetting the animation
                animateDismiss()
            }
            true //consume key press event so it doesn't get passed to activity onBackPressed()
        }
        this.view?.isFocusableInTouchMode = true //set focusable to enable key listening
        this.view?.requestFocus()

        blurDialogContainer.setOnTouchListener { _, motionEvent -> mDetector.onTouchEvent(motionEvent) } //for gesture listeners
        blurDialogContainer.afterMeasured {
            //set blurred image field to 33%
            val displayMetrics = resources.displayMetrics
            val height = displayMetrics.heightPixels / 3
            val explanationParams = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, height)
            val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, displayMetrics).toInt()
            explanationParams.setMargins(margin, margin, margin, margin)
            explanationContainer.layoutParams = explanationParams
            invalidate()
        }
        workoutExplanationTitle.text = mWorkoutTitle
        workoutTypeExplanation.text = mWorkoutDescription
        blurredImage.setImageBitmap(blur(context, resID))
        dialogWorkoutBookButton.setOnClickListener {
            navigator?.display(BookingLocalizationFragment.newInstance(trainingCreateData = TrainingCreateData(mWorkoutTitle)),tag = "Select Localization")
            dismiss()
        }

        mAdapter?.let {
            descriptionRecyclerView.adapter = it
            descriptionRecyclerView.overScrollMode = RecyclerView.OVER_SCROLL_NEVER //disable over scroll indicators
        }

        mAnimator?.cancel()
        val set = AnimatorSet()
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
        set.play(ObjectAnimator.ofFloat(explanationContainer, View.ALPHA, 0f, 1f))
                .with(ObjectAnimator.ofFloat(explanationContainer, View.Y, explanationContainer.x - 2 * margin, explanationContainer.x))
                .with(ObjectAnimator.ofFloat(descriptionLayout, View.Y,
                        resources.displayMetrics.heightPixels.toFloat(), (resources.displayMetrics.heightPixels / 3).toFloat() + 2 * margin))
        set.duration = ANIM_DURATION
        set.interpolator = DecelerateInterpolator()
        set.start()
        mAnimator = set
    }

    override fun onShowPress(p0: MotionEvent?) {
        //empty
    }

    override fun onSingleTapUp(p0: MotionEvent?): Boolean {
        //empty
        return true
    }

    override fun onLongPress(p0: MotionEvent?) {
        //empty
    }

    override fun onDown(p0: MotionEvent?): Boolean {
        //empty
        return true
    }

    override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
        if (Math.abs(vy) > MIN_FLING_THRESHOLD && e2.y - e1.y > MIN_SCROLL_DISTANCE)
            animateDismiss()
        return true
    }

    override fun onScroll(e1: MotionEvent, e2: MotionEvent, p2: Float, p3: Float): Boolean {
        if (e2.y - e1.y > MIN_SCROLL_DISTANCE)
            animateDismiss()
        return true
    }

    private fun animateDismiss() {
        val set = AnimatorSet()
        val margin = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16f, resources.displayMetrics)
        set.play(ObjectAnimator.ofFloat(explanationContainer, View.ALPHA, 1f, 0f))
                .with(ObjectAnimator.ofFloat(workoutTypeToolbarTitle, View.ALPHA, 1f, 0f))
                .with(ObjectAnimator.ofFloat(descriptionLayout, View.Y, (resources.displayMetrics.heightPixels / 3).toFloat() + 2 * margin,
                        resources.displayMetrics.heightPixels.toFloat()))
        set.duration = ANIM_DURATION
        set.interpolator = DecelerateInterpolator()
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                mAnimator = null
                dismiss()
            }

            override fun onAnimationCancel(animation: Animator?) {
                mAnimator = null
                dismiss()
            }
        })
        set.start()
        mAnimator = set
    }

    companion object {
        const val MIN_SCROLL_DISTANCE = 5
        const val MIN_FLING_THRESHOLD = 100
        const val ANIM_DURATION = 500.toLong()
        const val INFO_DIALOG_TAG = "blurDialog"
        const val RES_ID = "resID"
        fun show(@DrawableRes res: Int, fragmentManager: FragmentManager) = newInstance(res).also { it.show(fragmentManager, INFO_DIALOG_TAG) }

        private fun newInstance(@DrawableRes res: Int): BlurDialogFragment {
            val fragment = BlurDialogFragment()
            with(Bundle()) {
                putInt(RES_ID, res)
                fragment.arguments = this
            }
            return fragment
        }
    }
}
