
package com.novationmobile.view;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

public class SlidingMenuView extends FrameLayout {

    private SlidingView mSlider;

    public SlidingMenuView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public SlidingMenuView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SlidingMenuView(Context context) {
        super(context);
        init();
    }

    private void init() {

        mSlider= new SlidingView(getContext());
        LayoutParams layout= new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mSlider.setLayoutParams(layout);
        mSlider.setClickable(true);
        mSlider.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                mSlider.animateToggle();
            }
        });

        FrameLayout menu= new FrameLayout(getContext());
        menu.setId(R.id.novation__fragment_menu);
        layout= new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        menu.setLayoutParams(layout);

        FrameLayout main= new FrameLayout(getContext());
        main.setId(R.id.novation__fragment_main);
        layout= new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        main.setLayoutParams(layout);

        addView(menu);
        addView(mSlider);

        mSlider.addView(main);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        View menu= findViewById(R.id.novation__fragment_menu);
        menu.getLayoutParams().width= (int) (getWidth() * mSlider.getSliderWidth());
        menu.invalidate();
    }

    public void setMainFragment(Fragment fragment, FragmentManager manager) {
        manager.beginTransaction()
                .replace(R.id.novation__fragment_main, fragment)
                .commit();
    }

    public Fragment getMainFragment(FragmentManager manager) {
        return manager.findFragmentById(R.id.novation__fragment_main);
    }

    public void setMenuFragment(Fragment fragment, FragmentManager manager) {
        manager.beginTransaction()
                .replace(R.id.novation__fragment_menu, fragment)
                .commit();
    }

    public Fragment getMenuFragment(FragmentManager manager) {
        return manager.findFragmentById(R.id.novation__fragment_menu);
    }

    /**
     * Set the width of the menu.
     * 
     * @param menuWidth width relative to the parent between 0 and 1
     */
    public void setMenuWidth(float menuWidth) {
        if (menuWidth > 1f || menuWidth < 0f) {
            throw new IllegalArgumentException("MenuWidth must be between 0 and 1");
        }
        mSlider.setSliderWidth(menuWidth);
    }

    public float getMenuWidth() {
        return mSlider.getSliderWidth();
    }

    public boolean isOpen() {
        return mSlider.isOpen();
    }

    public void open() {
        mSlider.open();
    }

    public void animateOpen() {
        mSlider.animateOpen();
    }

    public void close() {
        mSlider.close();
    }

    public void animateClose() {
        mSlider.animateClose();
    }

    public void toggle() {
        mSlider.toggle();
    }

    public void animateToggle() {
        mSlider.animateToggle();
    }
}
