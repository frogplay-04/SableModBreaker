package com.FrogPlay.sablemobbreaker.goal;

import dev.ryanhcode.sable.sublevel.SubLevel;

public interface SubLevelTarget {
    SubLevel getTargetSubLevel();
    void stop();
}