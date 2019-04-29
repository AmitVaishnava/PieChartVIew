package com.piechartview.extra;

public class FrictionDynamics extends Dynamics {
    private float mFrictionFactor;

    public FrictionDynamics(final float frictionFactor) {
        mFrictionFactor = frictionFactor;
    }

    @Override
    protected void onUpdate(final int dt) {

        // then update the position based on the current velocity
        mPosition += mVelocity * dt / 1000;

        // and finally, apply some friction to slow it down
        mVelocity *= mFrictionFactor;
    }
}
