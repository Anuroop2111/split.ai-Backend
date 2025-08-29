package com.split.ai.split.service.core.engine;

import com.split.ai.split.service.core.utils.MoneyUtil;
import com.split.ai.split.service.model.request.expense.CreateExpenseRequest;
import io.split.engine.SplitInput;
import io.split.engine.SplitMode;
import io.split.engine.SplitPayload;

import java.util.List;

public final class SplitEngineAdapter {
    private SplitEngineAdapter() {}

    public static SplitInput toInput(CreateExpenseRequest req, int scale) {
        List<String> participants = req.getUserExpenseDetails().stream()
                .map(dto -> dto.getUserId().toString())
                .toList();

        SplitMode mode = SplitMode.valueOf(req.getSplitMode().name());
        SplitPayload payload = SplitPayloadFactory.build(req.getSplitMode(), req.getSplitRequest(), participants, scale);

        return new SplitInput(
                MoneyUtil.toMoney(req.getAmount(), scale),
                participants,
                mode,
                payload
        );
    }
}
