package com.tapdata.tm.commons.cdcdelay;

import io.tapdata.entity.event.TapEvent;

import java.util.List;
import java.util.function.Consumer;

/**
 *
 *
 * @author <a href="mailto:harsen_lin@163.com">Harsen</a>
 * @version v1.0 2022/8/4 10:40 Create
 */
public class CdcDelayDisable implements ICdcDelay {
    @Override
    public void addHeartbeatTable(List<String> tables) {

    }

    @Override
    public TapEvent filterAndCalcDelay(TapEvent tapEvent, Consumer<Long> delayConsumer) {
        return tapEvent;
    }
}
