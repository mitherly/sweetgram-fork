package org.telegram.margelet.drawer;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.margelet.MargeletConfig;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.ui.ActionBar.ActionBarLayout;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.DrawerLayoutContainer;
import org.telegram.ui.ActionBar.INavigationLayout;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.LaunchActivity;

public class DrawerContainer extends FrameLayout implements NotificationCenter.NotificationCenterDelegate {
    private final FrameLayout drawerPanel;
    private final DrawerHeaderView headerView;
    private final DrawerMenuView menuView;
    private final DrawerAccountPickerView accountPickerView;
    private final Paint scrimPaint;
    private final Rect rect = new Rect();
    private float progress = 0.0f;
    private boolean isOpen = false;
    private boolean tracking = false;
    private boolean startedEdgeSwipe = false;
    private boolean tapClosePending = false;
    private float startX = 0;
    private float startY = 0;
    private float startProgress = 0;
    private VelocityTracker velocityTracker;
    private ValueAnimator animator;
    private int drawerWidth;

    public DrawerContainer(Context context) {
        super(context);
        setVisibility(GONE);
        setWillNotDraw(false);

        scrimPaint = new Paint();
        scrimPaint.setColor(Color.BLACK);

        drawerPanel = new FrameLayout(context);
        drawerPanel.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        drawerPanel.setClickable(true);

        headerView = new DrawerHeaderView(context);
        menuView = new DrawerMenuView(context);
        menuView.setOnItemClick(() -> closeDrawer(true));

        accountPickerView = new DrawerAccountPickerView(context);
        accountPickerView.setOnAccountSelected(() -> closeDrawer(true));

        headerView.setOnChevronClick(() -> {
            accountPickerView.toggleExpand();
            headerView.setChevronExpanded(accountPickerView.isExpanded());
            menuView.setVisibility(accountPickerView.isExpanded() ? GONE : VISIBLE);
        });

        LinearLayout panelContent = new LinearLayout(context);
        panelContent.setOrientation(LinearLayout.VERTICAL);
        panelContent.addView(headerView, LayoutHelper.createLinear(-1, 160));
        panelContent.addView(accountPickerView, LayoutHelper.createLinear(-1, LayoutHelper.WRAP_CONTENT));
        panelContent.addView(menuView, LayoutHelper.createLinear(-1, LayoutHelper.MATCH_PARENT));

        drawerPanel.addView(panelContent, LayoutHelper.createFrame(-1, -1));
        addView(drawerPanel);

        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.mainUserInfoChanged);
    }

    public boolean isDrawerOpen() {
        return isOpen;
    }

    private void updateDrawerWidth() {
        int screenWidth = AndroidUtilities.displaySize.x;
        drawerWidth = Math.min(screenWidth - AndroidUtilities.dp(56), AndroidUtilities.dp(320));
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) drawerPanel.getLayoutParams();
        if (lp == null || lp.width != drawerWidth) {
            lp = new FrameLayout.LayoutParams(drawerWidth, FrameLayout.LayoutParams.MATCH_PARENT);
            drawerPanel.setLayoutParams(lp);
        }
    }

    public void openDrawer(boolean animated) {
        if (!MargeletConfig.classicDrawer()) {
            return;
        }
        isOpen = true;
        updateDrawerWidth();
        setVisibility(VISIBLE);
        refreshContents();
        if (animated) {
            animateProgress(1.0f);
        } else {
            setProgress(1.0f);
        }
    }

    public void closeDrawer(boolean animated) {
        isOpen = false;
        if (animated) {
            animateProgress(0.0f);
        } else {
            setProgress(0.0f);
            setVisibility(GONE);
        }
    }

    private void setProgress(float p) {
        progress = Math.max(0.0f, Math.min(1.0f, p));
        drawerPanel.setTranslationX((progress - 1.0f) * drawerWidth);
        scrimPaint.setAlpha((int) (128 * progress));
        invalidate();
    }

    private void animateProgress(float target) {
        if (animator != null) {
            animator.cancel();
        }
        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(250);
        animator.setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT);
        animator.addUpdateListener(animation -> setProgress((Float) animation.getAnimatedValue()));
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animator = null;
                if (progress <= 0.001f) {
                    setVisibility(GONE);
                }
            }
        });
        animator.start();
    }

    public void refreshContents() {
        headerView.updateUserInfo();
        accountPickerView.loadAccounts();
        LaunchActivity activity = LaunchActivity.instance;
        BaseFragment fragment = activity != null ? activity.getLastFragment() : null;
        menuView.rebuildMenu(UserConfig.selectedAccount, fragment);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (progress > 0.0f) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!MargeletConfig.classicDrawer()) return false;
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            startY = ev.getY();
            startProgress = progress;
            tracking = false;
            float visibleEdge = drawerPanel.getTranslationX() + drawerWidth;
            tapClosePending = isOpen && (ev.getX() > visibleEdge);
            return tapClosePending;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - startX;
            float dy = Math.abs(ev.getY() - startY);
            if (isOpen && dx < -AndroidUtilities.dp(6) && Math.abs(dx) > dy) {
                tracking = true;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!MargeletConfig.classicDrawer()) return false;
        int action = ev.getActionMasked();
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(ev);

        if (action == MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            startY = ev.getY();
            startProgress = progress;
            tracking = false;
            float visibleEdge = drawerPanel.getTranslationX() + drawerWidth;
            tapClosePending = isOpen && (ev.getX() > visibleEdge);
            return true;
        } else if (action == MotionEvent.ACTION_MOVE) {
            float dx = ev.getX() - startX;
            float dy = Math.abs(ev.getY() - startY);
            if (!tracking && Math.abs(dx) > AndroidUtilities.dp(6) && Math.abs(dx) > dy) {
                tracking = true;
            }
            if (tracking) {
                updateDrawerWidth();
                setProgress(startProgress + dx / drawerWidth);
                return true;
            }
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (tapClosePending && action == MotionEvent.ACTION_UP && Math.abs(ev.getX() - startX) < AndroidUtilities.dp(10)) {
                closeDrawer(true);
                tapClosePending = false;
                tracking = false;
                return true;
            }
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000);
                float vx = velocityTracker.getXVelocity();
                velocityTracker.recycle();
                velocityTracker = null;

                if (vx > 400) {
                    openDrawer(true);
                } else if (vx < -400) {
                    closeDrawer(true);
                } else {
                    if (progress > 0.4f) {
                        openDrawer(true);
                    } else {
                        closeDrawer(true);
                    }
                }
            } else {
                if (progress > 0.4f) {
                    openDrawer(true);
                } else {
                    closeDrawer(true);
                }
            }
            tracking = false;
            tapClosePending = false;
            return true;
        }
        return false;
    }

    private boolean canStartClosedDrawerSwipe(MotionEvent motionEvent) {
        LaunchActivity activity = LaunchActivity.instance;
        if (activity == null) return false;
        INavigationLayout parentActionBarLayout = activity.getActionBarLayout();
        if (parentActionBarLayout == null || parentActionBarLayout.getFragmentStack().size() != 1 || !parentActionBarLayout.allowSwipe()) {
            return false;
        }
        BaseFragment lastFragment = parentActionBarLayout.getLastFragment();
        if (!(lastFragment instanceof DialogsActivity)) {
            return false;
        }
        ViewGroup view = parentActionBarLayout.getView();
        if (view == null) return false;
        view.getHitRect(rect);
        if (rect.contains((int) motionEvent.getX(), (int) motionEvent.getY()) && findScrollingChild(view, motionEvent.getX() - rect.left, motionEvent.getY() - rect.top) == null) {
            return true;
        }
        return false;
    }

    private View findScrollingChild(ViewGroup viewGroup, float f, float f2) {
        int childCount = viewGroup.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View childAt = viewGroup.getChildAt(i);
            if (childAt.getVisibility() == 0) {
                childAt.getHitRect(rect);
                if (rect.contains((int) f, (int) f2)) {
                    if (childAt.canScrollHorizontally(-1)) {
                        return childAt;
                    }
                    if (childAt instanceof ViewGroup) {
                        Rect r = new Rect(rect);
                        View viewFindScrollingChild = findScrollingChild((ViewGroup) childAt, f - r.left, f2 - r.top);
                        if (viewFindScrollingChild != null) {
                            return viewFindScrollingChild;
                        }
                    }
                }
            }
        }
        return null;
    }

    private float getDrawerOpenTouchSlop() {
        return AndroidUtilities.getPixelsInCM(0.2f, true);
    }

    private boolean shouldBlockClosedDrawerSwipe(float f, float f2) {
        float fAbs = Math.abs(f2);
        float drawerOpenTouchSlop = AndroidUtilities.touchSlop;
        if (drawerOpenTouchSlop <= 0.0f) {
            drawerOpenTouchSlop = getDrawerOpenTouchSlop();
        }
        return fAbs >= drawerOpenTouchSlop && fAbs > Math.abs(f);
    }

    private boolean shouldStartClosedDrawerTracking(float f, float f2) {
        return f > 0.0f && f / 3.0f > f2 && Math.abs(f) >= getDrawerOpenTouchSlop();
    }

    private void beginClosedDrawerTracking(MotionEvent motionEvent, float f) {
        tracking = true;
        tapClosePending = false;
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        setVisibility(VISIBLE);
        refreshContents();
        updateDrawerWidth();
        startX += Math.signum(f) * getDrawerOpenTouchSlop();
        startY = motionEvent.getY();
        startProgress = progress;
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        } else {
            velocityTracker.clear();
        }
        velocityTracker.addMovement(motionEvent);
        ViewParent p = getParent();
        if (p != null) {
            p.requestDisallowInterceptTouchEvent(true);
        }
    }

    public boolean handleEdgeSwipeIntercept(MotionEvent motionEvent) {
        if (!MargeletConfig.classicDrawer()) {
            return false;
        }
        if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
            startX = motionEvent.getX();
            startY = motionEvent.getY();
            startProgress = progress;
            startedEdgeSwipe = false;
            tracking = false;
            if (canStartClosedDrawerSwipe(motionEvent)) {
                startedEdgeSwipe = true;
                if (velocityTracker == null) {
                    velocityTracker = VelocityTracker.obtain();
                }
                velocityTracker.clear();
                velocityTracker.addMovement(motionEvent);
            }
            return false;
        }
        if (startedEdgeSwipe) {
            if (velocityTracker != null) {
                velocityTracker.addMovement(motionEvent);
            }
            if (motionEvent.getAction() == MotionEvent.ACTION_MOVE) {
                float x = motionEvent.getX() - startX;
                float y = motionEvent.getY() - startY;
                if (shouldBlockClosedDrawerSwipe(x, y)) {
                    startedEdgeSwipe = false;
                    return false;
                }
                if (shouldStartClosedDrawerTracking(x, Math.abs(y))) {
                    beginClosedDrawerTracking(motionEvent, x);
                    return true;
                }
            }
            if (motionEvent.getAction() == MotionEvent.ACTION_UP || motionEvent.getAction() == MotionEvent.ACTION_CANCEL) {
                startedEdgeSwipe = false;
            }
        }
        return false;
    }

    public boolean handleEdgeSwipeTouch(MotionEvent motionEvent) {
        if (!MargeletConfig.classicDrawer()) {
            return false;
        }
        if (!startedEdgeSwipe && !tracking) {
            return false;
        }
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain();
        }
        velocityTracker.addMovement(motionEvent);
        int action = motionEvent.getAction();
        if (action != MotionEvent.ACTION_UP) {
            if (action == MotionEvent.ACTION_MOVE) {
                if (tracking) {
                    updateDrawerWidth();
                    setProgress(Math.max(0.0f, Math.min(1.0f, startProgress + ((motionEvent.getX() - startX) / drawerWidth))));
                }
                return true;
            }
            if (action != MotionEvent.ACTION_CANCEL) {
                return true;
            }
        }
        if (tracking) {
            velocityTracker.computeCurrentVelocity(1000);
            float vx = velocityTracker.getXVelocity();
            float vy = velocityTracker.getYVelocity();
            velocityTracker.recycle();
            velocityTracker = null;

            if ((progress >= 0.2f || (vx >= 400 && Math.abs(vx) >= Math.abs(vy))) && (vx >= 0.0f || Math.abs(vx) < 400)) {
                openDrawer(true);
            } else {
                closeDrawer(true);
            }
        }
        startedEdgeSwipe = false;
        tracking = false;
        return true;
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) {
            drawerPanel.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            headerView.updateColors();
            menuView.updateColors();
            invalidate();
        } else if (id == NotificationCenter.mainUserInfoChanged) {
            headerView.updateUserInfo();
        }
    }
}
