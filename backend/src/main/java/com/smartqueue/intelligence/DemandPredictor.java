package com.smartqueue.intelligence;

import com.smartqueue.dto.InsightDtos.DemandForecastDto;

/** Port: replace the statistical model with a trained one later without touching the service/controller. */
public interface DemandPredictor {
    DemandForecastDto predict(DemandInput input);
}
