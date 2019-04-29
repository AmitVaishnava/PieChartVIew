package com.piechartview.views;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;

import com.piechartview.adapter.BasePieChartAdapter;
import com.piechartview.extra.Dynamics;
import com.piechartview.extra.UiUtils;

public class PieChartView extends SurfaceView implements SurfaceHolder.Callback {
    
    public final String TAG = this.getClass().getSimpleName();

    public enum PieChartAnchor {
    	
        TOP (270),
        RIGHT (0),
        BOTTOM (90),
        LEFT (180);
        
        private float degrees;
        
        PieChartAnchor(float degrees) {
            this.degrees = degrees;
        }
    };

    private static final int SUB_STROKE_WIDTH = 1;
    private static final int INFO_STROKE_WIDTH = 3;
    private static final int PIXELS_PER_SECOND = 1000;
    private static final float VELOCITY_TOLERANCE = 40f;
    private static final int INVALID_INDEX = -1;
    public static final int TOUCH_STATE_RESTING = 0;
    private static final int TOUCH_STATE_CLICK = 1;
    public static final int TOUCH_STATE_ROTATE = 2;
    public static final int CHART_HIDDEN = 0;
    public static final int CHART_SHOWING = 1;
    public static final int CHART_INVALID = 2;
    private static final float DEFAULT_SNAP_DEGREE = 0f;
	private DrawThread mDrawThread;
    private int mTouchState = TOUCH_STATE_RESTING;
    private int mScrollThreshold;
    private VelocityTracker mVelocityTracker;
    private Dynamics mDynamics;
    private Runnable mDynamicsRunnable;
    private BasePieChartAdapter mAdapter;
    private int mTouchStartX;
    private int mTouchStartY;
    private float mSnapToDegree = DEFAULT_SNAP_DEGREE;
    private float mRotationStart = 0;
    private float mLastRotation = 0;
    private boolean mRotatingClockwise;
    private int mChartDiameter;
	private float mInfoRadius;
    private float mPixelDensity;
    private PointF mCenter = new PointF();
	private float mStrokeWidth;
	private float mRotationDegree = 0;
	private float mChartScale = 1.0f;
	private boolean mChartHidden = false;
	private boolean mNeedsToggle = false;
	private boolean mNeedsUpdate = false;
	private boolean mShowInfo = false;
	private boolean mLoaded = false;
	private List<PieSliceDrawable> mDrawables;
	private LinkedList<PieSliceDrawable> mRecycledDrawables;
	private int mCurrentIndex;
	private Bitmap mDrawingCache;
	private OnPieChartChangeListener mOnPieChartChangeListener;
	private OnPieChartReadyListener mOnPieChartReadyListener;

    private OnItemClickListener mOnItemClickListener;
    private OnRotationStateChangeListener mOnRotationStateChangeListener;
	private AdapterDataSetObserver mDataSetObserver;
	private Handler mHandler;
	private Paint mPaint;
	private Paint mStrokePaint;
	
	private void setTouchState(int touchState) {
		
		mTouchState = touchState;
		
		if (mOnRotationStateChangeListener != null) {
        	mOnRotationStateChangeListener.onRotationStateChange(mTouchState);
        }
	}
	
	public Bitmap getDrawingCache() {
		return mDrawingCache;
	}

    public void setDynamics(final Dynamics dynamics) {
    	
        if (mDynamics != null) {
            dynamics.setState((float) getRotationDegree(), mDynamics.getVelocity(), AnimationUtils
                    .currentAnimationTimeMillis());
        }
        
        mDynamics = dynamics;
    }

	void setRotationDegree(float rotationDegree) {
		
		// Keep rotation degree positive
		if (rotationDegree < 0) rotationDegree += 360;
		
		// Keep rotation degree between 0 - 360
		mRotationDegree = rotationDegree % 360;
	}
	
	public float getRotationDegree() {
		return mRotationDegree;
	}

	public int getCurrentIndex() {
		
		if (!isLoaded()) return 0;
		
		return mCurrentIndex;
	}

	private void setCurrentIndex(final int index) {
		
		mCurrentIndex = index;

		if (mNeedsToggle) {
			mNeedsToggle = false;
			toggleChart();
		}
		
		if (mOnPieChartChangeListener != null && !mChartHidden && isLoaded()) {
			
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {

					mOnPieChartChangeListener.onSelectionChanged(index);
				}
			});
		}
	}
	
	public void setSnapToAnchor(PieChartAnchor anchor) {
		mSnapToDegree = anchor.degrees;
		snapTo();
	}
	
	public float getChartDiameter() {
		return mChartDiameter;
	}
	
	public float getChartRadius() {
		return mChartDiameter / 2f;
	}
	
	public synchronized boolean isLoaded() {
		return mLoaded;
	}

	public synchronized void setLoaded(boolean mLoaded) {
		this.mLoaded = mLoaded;
	}

	public void setSelection(int index) {
		animateTo(index);
	}

	public DrawThread getDrawThread() {
		return mDrawThread;
	}
	
	public void onPause() {
		mDrawThread.onPause();
	}
	
	public void onResume() {
		mDrawThread.onResume();
	}

	public OnPieChartChangeListener getOnPieChartChangeListener() {
		return mOnPieChartChangeListener;
	}

	public void setOnPieChartChangeListener(
			OnPieChartChangeListener mOnPieChartChangeListener) {
		this.mOnPieChartChangeListener = mOnPieChartChangeListener;
	}

	public void setOnPieChartReadyListener(
			OnPieChartReadyListener mOnPieChartReadyListener) {
		this.mOnPieChartReadyListener = mOnPieChartReadyListener;
	}
	
	public OnRotationStateChangeListener getOnRotationStateChangeListener() {
		return mOnRotationStateChangeListener;
	}

	public void setOnRotationStateChangeListener(
			OnRotationStateChangeListener mOnRotationStateChangeListener) {
		this.mOnRotationStateChangeListener = mOnRotationStateChangeListener;
	}

    public OnItemClickListener getOnItemClickListener() {
		return mOnItemClickListener;
	}

	public void setOnItemClickListener(OnItemClickListener mOnItemClickListener) {
		this.mOnItemClickListener = mOnItemClickListener;
	}

	public PieSliceDrawable getSlice(int index) {
	
		synchronized(mDrawables) {
			
			if (mDrawables.size() > index) {
				return mDrawables.get(index);
			}
		}
		
		return null;
	}
	
	public PieChartView(Context context) {
		super(context);
		
		init();
	}
	
	public PieChartView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		init();
	}
	
	public PieChartView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		
		init();
	}

	public PieChartView(Context context, OnPieChartReadyListener listener) {
		super(context);
		
		setOnPieChartReadyListener(listener);
		init();
	}
	
	private void init() {

		Context context = getContext();
		
		mHandler = new Handler();
		
        getHolder().addCallback(this);
		setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSPARENT);
        
        mDrawThread = new DrawThread(getHolder(), mHandler);
		
		mScrollThreshold = ViewConfiguration.get(context).getScaledTouchSlop();
		mPixelDensity = UiUtils.getDisplayMetrics(context).density;
		mStrokeWidth = UiUtils.getDynamicPixels(context, SUB_STROKE_WIDTH);
		
		mDrawables = new ArrayList<PieSliceDrawable>();
		mRecycledDrawables = new LinkedList<PieSliceDrawable>();
		
		initPaints();
	}
	
	private void initPaints() {

		mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPaint.setColor(Color.WHITE);
		
		mStrokePaint = new Paint(mPaint);
		mStrokePaint.setStyle(Paint.Style.STROKE);
		mStrokePaint.setStrokeWidth(UiUtils.getDynamicPixels(getContext(), INFO_STROKE_WIDTH));
		mStrokePaint.setColor(Color.BLACK);
		mStrokePaint.setAlpha(50);
	}

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
    	
        if ((!inCircle((int) event.getX(), (int) event.getY()) && 
				mTouchState == TOUCH_STATE_RESTING)) {
        	return false;
        }
        
        switch (event.getAction()) {
        
            case MotionEvent.ACTION_DOWN:
                startTouch(event);
                break;

            case MotionEvent.ACTION_MOVE:
            	
                if (mTouchState == TOUCH_STATE_CLICK) {
                    startScrollIfNeeded(event);
                }
                
                if (mTouchState == TOUCH_STATE_ROTATE) {
                    mVelocityTracker.addMovement(event);
                    rotateChart(event.getX(), event.getY());
                }
                
                break;

            case MotionEvent.ACTION_UP:
            	
            	float velocity = 0;
            	
                if (mTouchState == TOUCH_STATE_CLICK) {
                	
                    clickChildAt((int) event.getX(), (int) event.getY());
                    
                } else if (mTouchState == TOUCH_STATE_ROTATE) {
                	
                    mVelocityTracker.addMovement(event);
                    mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND);

                    velocity = calculateVelocity();
                }

                endTouch(event.getX(), event.getY(), velocity);
                
                break;

            default:
                endTouch(event.getX(), event.getY(), 0);
                break;
        }
        
        return true;
    }

	private float calculateVelocity() {
		
		int direction = mRotatingClockwise ? 1 : -1;
        
        float velocityX = mVelocityTracker.getXVelocity() / mPixelDensity;
        float velocityY = mVelocityTracker.getYVelocity() / mPixelDensity;
        float velocity = (float) Math.sqrt(velocityX * velocityX + velocityY * velocityY) * direction / 2;
        
        return velocity;
	}

    private void startTouch(final MotionEvent event) {
    	
        // user is touching the list -> no more fling
        removeCallbacks(mDynamicsRunnable);
        
        mLastRotation = getRotationDegree();
    	
        // save the start place
        mTouchStartX = (int) event.getX();
        mTouchStartY = (int) event.getY();

        mVelocityTracker = VelocityTracker.obtain();
        mVelocityTracker.addMovement(event);

        setTouchState(TOUCH_STATE_CLICK);
    }

    private void endTouch(final float x, final float y, final float velocity) {
    	
        // recycle the velocity tracker
    	if (mVelocityTracker != null) {
	        mVelocityTracker.recycle();
	        mVelocityTracker = null;
    	}


        // create the dynamics runnable if we haven't before
        if (mDynamicsRunnable == null) {
        	
            mDynamicsRunnable = new Runnable() {
            	
                public void run() {
                	
                    // if we don't have any dynamics set we do nothing
                    if (mDynamics == null) {
                        return;
                    }
                    

                    mDynamics.update(AnimationUtils.currentAnimationTimeMillis());

                    // Keep the rotation amount between 0 - 360
                    rotateChart(mDynamics.getPosition() % 360);

                    if (!mDynamics.isAtRest(VELOCITY_TOLERANCE)) {

                        postDelayed(this, 8);
                        
                    } else {

                    	snapTo();
                    }

                }
            };
        }

        if (mDynamics != null && Math.abs(velocity) > ViewConfiguration.get(getContext()).getScaledMinimumFlingVelocity()) {
            mDynamics.setState((float) getRotationDegree(), velocity, AnimationUtils.currentAnimationTimeMillis());
            post(mDynamicsRunnable);
            
        } else if (mTouchState != TOUCH_STATE_CLICK) {
        	
        	snapTo();
        }
        
        // reset touch state
        setTouchState(TOUCH_STATE_RESTING);
    }

    private boolean startScrollIfNeeded(final MotionEvent event) {
    	
        final int xPos = (int) event.getX();
        final int yPos = (int) event.getY();
        
        if (isEnabled()
        		&& (xPos < mTouchStartX - mScrollThreshold
                || xPos > mTouchStartX + mScrollThreshold
                || yPos < mTouchStartY - mScrollThreshold
                || yPos > mTouchStartY + mScrollThreshold)) {
            
            setTouchState(TOUCH_STATE_ROTATE);
            
            mRotationStart = (float) Math.toDegrees(Math.atan2(mCenter.y - yPos, mCenter.x - xPos));
            
            return true;
        }
        
        return false;
    }

    private void clickChildAt(final int x, final int y) {
    	
    	if (!isEnabled()) return;
    	
        final int index = getContainingChildIndex(x, y);

        if (index != INVALID_INDEX) {
        	
            final PieSliceDrawable sliceView = mDrawables.get(index);
            final long id = mAdapter.getItemId(index);
            boolean secondTap = false;
            
            if (getCurrentIndex() != index) {
            	animateTo(sliceView, index);
            } else {
            	secondTap = true;
            }

            playSoundEffect(SoundEffectConstants.CLICK);
            performItemClick(secondTap, sliceView, index, id);
        }
    }

    public boolean performItemClick(boolean secondTap, PieSliceDrawable view, int position, long id) {
    	
        if (mOnItemClickListener != null) {
        	
            mOnItemClickListener.onItemClick(secondTap, this, view, position, id);
            
            return true;
        }

        return false;
    }

    private int getContainingChildIndex(final int x, final int y) {

    	if (!inCircle(x, y)) return INVALID_INDEX;
        final Bitmap viewBitmap = getDrawingCache();
        
        if (viewBitmap == null) return INVALID_INDEX;
        int pixel = viewBitmap.getPixel(x, y);
        
        for (int index = 0; index < mDrawables.size(); index++) {
        	
            final PieSliceDrawable slice = mDrawables.get(index);
            
            if (slice.getSliceColor() == pixel) {
                return index;
            }
        }
        
        return INVALID_INDEX;
    }

    private boolean inCircle(final int x, final int y) {
    	
    	if ((mChartHidden || !isLoaded())) return false;
    	
        double dx = (x - mCenter.x) * (x - mCenter.x);
        double dy = (y - mCenter.y) * (y - mCenter.y);

        if ((dx + dy) < ((mChartDiameter / 2) * (mChartDiameter / 2))) {
            return true;
        } else {
            return false;
        }
    }
	
	@Override
	public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		
		int width = MeasureSpec.getSize(widthMeasureSpec);
		int height = MeasureSpec.getSize(heightMeasureSpec);
		
        boolean useHeight = height < width;
        
        mChartDiameter = (useHeight ? (height - (getPaddingTop() + getPaddingBottom()))
        		: (width - (getPaddingLeft() + getPaddingRight()))) - (int) mStrokeWidth;
		
		mInfoRadius = getChartRadius() / 2;
		
        int size = useHeight ? height : width;
        
		setMeasuredDimension(size, size);
	}
	
	@Override
	public void onLayout(boolean changed, int left, int top, int right, int bottom) {
		super.onLayout(changed, left, top, right, bottom);
        
		// Get the center coordinates of the view
		mCenter.x = (float) Math.abs(left - right) / 2;
		mCenter.y = (float) Math.abs(top - bottom) / 2;
	}

    private void addPieSlices() {
    	
    	synchronized (mDrawables) {
    		
	    	float offset = 0;
	    	
	        for (int index = 0; index < mAdapter.getCount(); index++) {
	            
	        	// Check for any recycled PieSliceDrawables
	        	PieSliceDrawable recycled = getRecycledSlice();
	        	
	        	// Get the slice from the adapter
	            final PieSliceDrawable childSlice = mAdapter.getSlice(this, recycled, index, offset);
	            
	            childSlice.setBounds(getBounds());
	            mDrawables.add(childSlice);
	            
	            offset += childSlice.getDegrees();
	        }
	        
	        setLoaded(true);
    	}
    }

    private Rect getBounds() {

        int left = (int) (mCenter.x - getChartRadius());
        int top = (int) (mCenter.y - getChartRadius());
        
    	return new Rect(left, top, left + mChartDiameter, top + mChartDiameter);
    }

    private PieSliceDrawable getRecycledSlice() {
        
    	if (mRecycledDrawables.size() != 0) {
            return mRecycledDrawables.removeFirst();
        }
        
        return null;
    }

    public int toggleChart() {
    	
    	final float start = mChartScale;
    	final float end = start == 1f ? 0f : 1f;
		
		if (mChartHidden && !isLoaded()) {
			
			mNeedsToggle = true;
			
			return CHART_INVALID;
		}

		mNeedsToggle = false;
		mChartHidden = (end == 0);
    	
    	ThreadAnimator scale = ThreadAnimator.ofFloat(start, end);
    	scale.setAnimationListener(new ThreadAnimator.AnimationListener() {
			
			@Override
			public void onAnimationEnded() {
				mDrawingCache = null;
				
				if (mNeedsUpdate) {
					mNeedsUpdate = false;
					resetChart();
				}
			}
		});
    	
    	scale.setDuration(400);
    	
    	if (end == 1) {
    		scale.setInterpolator(new OvershootInterpolator());
    	}
    	
    	getDrawThread().setScaleAnimator(scale);
    	
    	return (end == 0) ? CHART_HIDDEN : CHART_SHOWING;
    }

    private void snapTo() {
    	snapTo(true);
    }

    private void snapTo(boolean animated) {
    	
    	for (int index = 0; index < mDrawables.size(); index++) {
        	
            final PieSliceDrawable slice = mDrawables.get(index);
            
            if (slice.containsDegree(mRotationDegree, mSnapToDegree)) {
            	
            	rotateChart(slice, index, animated);
            	
            	break;
            }
    	}
    }

    private void animateTo(int index) {
    	rotateChart(null, index, true);
    }
    
    private void animateTo(PieSliceDrawable slice, int index) {
    	rotateChart(slice, index, true);
    }
    

    private void animateTo(float start, float end) {

    	ThreadAnimator rotate = ThreadAnimator.ofFloat(start, end);
    	rotate.setDuration(300);
    	rotate.setAnimationListener(new ThreadAnimator.AnimationListener() {
			
			@Override
			public void onAnimationEnded() {
				
				if (mOnRotationStateChangeListener != null) {
		        	mOnRotationStateChangeListener.onRotationStateChange(TOUCH_STATE_RESTING);
		        }
				
				mDrawingCache = null;
			}
		});
    	
    	if (mOnRotationStateChangeListener != null) {
        	mOnRotationStateChangeListener.onRotationStateChange(TOUCH_STATE_ROTATE);
        }
    	
    	getDrawThread().setRotateAnimator(rotate);
    }
    
    private void rotateChart(PieSliceDrawable slice, int index, boolean animated) {
    	
    	synchronized (mDrawables) {
    		
	    	if (mDrawables.size() == 0
	    			|| mDrawables.size() <= index
	    			|| !isEnabled()) return;
	    	
	    	if (slice == null) {
	    		slice = mDrawables.get(index);
	    	}
	        float degree = slice.getSliceCenter();

	    	degree = mSnapToDegree - degree;
	    	if (degree < 0) degree += 360;
	    	
	    	float start = getRotationDegree();
	    	float rawDiff = Math.abs(start - degree);
	    	float modDiff = rawDiff % 360f;
	    	
	    	if (modDiff > 180.0) {
	    		start = start > degree ? (360 - start) * -1 : (360 + start);
	    	}
	
	    	if (animated) {
	    		animateTo(start, degree);
	    	} else {
	    		setRotationDegree(degree);
				mDrawingCache = null;
	    	}
	    	
			setCurrentIndex(index);
    	}
    }

    private void rotateChart(final float x, final float y) {
    	
    	float degree = (float) (Math.toDegrees(Math.atan2(mCenter.y - y, mCenter.x - x)) - mRotationStart);
    	
    	// Rotate from the last rotation position to prevent rotation jumps
    	rotateChart(mLastRotation + degree);
    }

    private void rotateChart(float degree) {
    	
    	final float previous = getRotationDegree();
    	
    	setRotationDegree(degree);

    	setRotatingClockwise(previous);
    }

    private void setRotatingClockwise(float previous) {

    	final float change = (mRotationDegree - previous);
    	mRotatingClockwise = (change > 0 && Math.abs(change) < 300) || (Math.abs(change) > 300 && mRotatingClockwise);
    }

	public BasePieChartAdapter getPieChartAdapter() {
		return mAdapter;
	}

	public void setAdapter(BasePieChartAdapter adapter) {
		
		// Unregister the old data change observer
		if (mAdapter != null && mDataSetObserver != null) {
			mAdapter.unregisterDataSetObserver(mDataSetObserver);
		}
		
		// Perform validation check
		float total = validAdapter(adapter);
		if ((1f - total) > 0.0001f) {
			return;
		}
		
		resetChart();
		
		mAdapter = adapter;
		
		// Register the data change observer
		if (mAdapter != null) {
			mDataSetObserver = new AdapterDataSetObserver();
			mAdapter.registerDataSetObserver(mDataSetObserver);
		}
	}

	private float validAdapter(BasePieChartAdapter adapter) {
		
		float total = 0;
		
		for (int i = 0; i < adapter.getCount(); i++) {
			total += adapter.getPercent(i);
		}
		return total;
	}
	
	/**
	 * Resets the chart and recycles all PieSliceDrawables
	 */
	private void resetChart() {
		
		synchronized(mDrawables) {
			
			setLoaded(false);
			
			mRecycledDrawables.addAll(mDrawables);
			mDrawables.clear();
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width,
			int height) {
	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		
		if (mDrawThread.getState() == Thread.State.TERMINATED) {
			
			mDrawThread = new DrawThread(getHolder(), mHandler);
			mDrawThread.setRunning(true);
			mDrawThread.start();
			
        } else {
        	
        	mDrawThread.setRunning(true);
        	mDrawThread.start();
        }
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		
		boolean retry = true;

		mDrawThread.onResume();
		mDrawThread.setRunning(false);
		
		while (retry) {
			try {
				mDrawThread.join();
				retry = false;
			} catch (InterruptedException e) {}
		}
	}

	protected class DrawThread extends Thread {

		private SurfaceHolder mSurfaceHolder;
		private boolean mIsRunning;
		private Object mPauseLock = new Object();
		private boolean mPaused;
		private ThreadAnimator mRotateAnimator, mScaleAnimator;
		private Handler mHandler;

		public DrawThread(SurfaceHolder surfaceHolder, Handler handler) {
			this.mSurfaceHolder = surfaceHolder;
			this.mHandler = handler;
			mIsRunning = false;
			mPaused = true;
		}

		public void setRunning(boolean run) {
			mIsRunning = run;
		}
		
		public boolean isRunning() {
			return mIsRunning;
		}
		
		public boolean isPaused() {
			return mPaused;
		}
		
		public void setRotateAnimator(ThreadAnimator mRotateAnimator) {
			this.mRotateAnimator = mRotateAnimator;
			mRotateAnimator.start();
		}

		public void setScaleAnimator(ThreadAnimator mScaleAnimator) {
			this.mScaleAnimator = mScaleAnimator;
			mScaleAnimator.start();
		}

		/**
		 * Pause the drawing to the SurfaceView
		 */
		public void onPause() {
			
			if (mPaused) return;
			
		    synchronized (mPauseLock) {
		    	cleanUp();
		        mPaused = true;
		    }
		}

		/**
		 * Resume drawing to the SurfaceView
		 */
		public void onResume() {

			if (!mPaused) return;
			
		    synchronized (mPauseLock) {
		        mPaused = false;
		        mPauseLock.notifyAll();
		    }
		}
		
		@Override
		public void run() {
			
			// Notify any listener the thread is ready and running
			mHandler.post(new Runnable() {
				
				@Override
				public void run() {

					if (mOnPieChartReadyListener != null) {
						mOnPieChartReadyListener.onPieChartReady();
					}
				}
			});
			
			Canvas canvas;
			
			while (mIsRunning) {
				
				// Check for a pause lock
				synchronized (mPauseLock) {
				    while (mPaused) {
				        try {
				            mPauseLock.wait();
				        } catch (InterruptedException e) {
				        	Log.e(TAG, "Interrupted", e);
				        }
				    }
				}
				if (mDrawables.size() == 0 && mAdapter != null) {
					
					addPieSlices();
					buildDrawingCache();
					snapTo();
				}

				if (mDrawingCache == null) {
					buildDrawingCache();
				}
				
				canvas = null;
				
				try {
					
					canvas = mSurfaceHolder.lockCanvas(null);
					
					synchronized (mSurfaceHolder) {
						
						if (canvas != null && !mPaused) {

							updateAnimators();
							canvas.drawColor(0, PorterDuff.Mode.CLEAR);
					    	doDraw(canvas, mRotationDegree, mChartScale, mShowInfo);
						}
					}
					
				} finally {
					if (canvas != null) {
						mSurfaceHolder.unlockCanvasAndPost(canvas);
					}
				}
			}
		}

		private void updateAnimators() {
			
			if (mRotateAnimator != null && mRotateAnimator.isRunning()) {
				setRotationDegree(mRotateAnimator.floatUpdate());
			}

			if (mScaleAnimator != null && mScaleAnimator.isRunning()) {
				mChartScale = mScaleAnimator.floatUpdate();
			}
		}

		private void cleanUp() {
			
			Canvas canvas = null;
			
			try {
				
				canvas = mSurfaceHolder.lockCanvas(null);
				
				synchronized (mSurfaceHolder) {
					if (canvas != null) {
						canvas.drawColor(0, PorterDuff.Mode.CLEAR);
					}
				}
				
			} finally {
				if (canvas != null) {
					mSurfaceHolder.unlockCanvasAndPost(canvas);
				}
			}
		}

		private void buildDrawingCache() {
			
			if (mDrawingCache == null) {
				mDrawingCache = Bitmap.createBitmap(getWidth(), getHeight(), Bitmap.Config.ARGB_8888);
			}
			
			Canvas cache = new Canvas(mDrawingCache);
	    	doDraw(cache, mRotationDegree, mChartScale, mShowInfo);
		}


		public void doDraw(Canvas canvas) {
			doDraw(canvas, mRotationDegree, mChartScale, mShowInfo);
		}

		private void doDraw(Canvas canvas, float rotationDegree, float scale, boolean showInfo) {
			
			if (canvas == null || mAdapter == null) return;
			
			if (scale != 0) {
				
				// Scale and rotate the canvas
				canvas.save();
				canvas.scale(scale, scale, mCenter.x, mCenter.y);
				canvas.rotate(rotationDegree, mCenter.x, mCenter.y);
		    	canvas.translate(getPaddingLeft(), getPaddingTop());
		    	
		    	// Draw a background circle
				canvas.drawCircle(mCenter.x, mCenter.y, getChartRadius() + mStrokeWidth, mPaint);
		    	
				// Draw all of the pie slices
				synchronized (mDrawables) {
			        for (PieSliceDrawable slice : mDrawables) {
			        	slice.draw(canvas);
			        }
				}
		        
		        canvas.restore();
			}
		}
	}
	
	/**
	 * Interfaces Used
	 */
	
	public interface OnPieChartChangeListener {
		public void onSelectionChanged(int index);
	}
	
	public interface OnItemClickListener {
		public void onItemClick(boolean secondTap, View parent, Drawable drawable, int position,
                                long id);
	}
	
	public interface OnRotationStateChangeListener {
		public void onRotationStateChange(int state);
	}
	
	public interface OnPieChartReadyListener {
		public void onPieChartReady();
	}
	
	class AdapterDataSetObserver extends DataSetObserver {

		private Parcelable mInstanceState = null;

		@Override
		public void onChanged() {

			if (mChartScale != 0f) {
				mNeedsUpdate = true;
				return;
			}
			
        	resetChart();
			
			// Detect the case where a cursor that was previously invalidated
			// has been re-populated with new data.
			if (PieChartView.this.getPieChartAdapter().hasStableIds() && mInstanceState != null) {
				
				PieChartView.this.onRestoreInstanceState(mInstanceState);
				mInstanceState = null;
			}
		}

		@Override
		public void onInvalidated() {

			if (PieChartView.this.getPieChartAdapter().hasStableIds()) {
				
				// Remember the current state for the case where our hosting
				// activity is being stopped and later restarted
				mInstanceState = PieChartView.this.onSaveInstanceState();
			}
			
			if (mChartScale != 0f) {
				mNeedsUpdate = true;
				return;
			}
			
			resetChart();
		}

		public void clearSavedState() {
			mInstanceState = null;
		}
	}
}
