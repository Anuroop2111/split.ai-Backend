package com.split.ai.split.service.core.engine;

import com.split.ai.split.service.core.utils.MoneyUtil;
import com.split.ai.split.service.model.enums.SPLIT_MODE;
import com.split.ai.split.service.model.request.split.ExactSplitRequest;
import com.split.ai.split.service.model.request.split.PercentageSplitRequest;
import com.split.ai.split.service.model.request.split.RatioSplitRequest;
import com.split.ai.split.service.model.request.split.SplitRequest;
import io.split.engine.EqualPayload;
import io.split.engine.ExactPayload;
import io.split.engine.PercentPayload;
import io.split.engine.RatioPayload;
import io.split.engine.SplitPayload;
import io.split.engine.UserAmount;
import io.split.engine.UserBps;
import io.split.engine.UserRatio;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class SplitPayloadFactory {

    private SplitPayloadFactory() {}

    @FunctionalInterface
    private interface PayloadBuilder {
        SplitPayload build(SplitRequest request, List<String> participants, int scale);
    }

    private static final Map<SPLIT_MODE, PayloadBuilder> BUILDERS = new EnumMap<>(SPLIT_MODE.class);

    static {
        BUILDERS.put(SPLIT_MODE.EQUAL, (req, participants, scale) -> new EqualPayload(participants));
        BUILDERS.put(SPLIT_MODE.EXACT, (req, participants, scale) -> {
            ExactSplitRequest eReq = (ExactSplitRequest) req;
            List<UserAmount> items = eReq.getUserAmountSplitDtoList().stream()
                    .map(u -> new UserAmount(u.getUserId().toString(), MoneyUtil.toMoney(u.getAmount(), scale)))
                    .collect(Collectors.toList());
            return new ExactPayload(items);
        });
        BUILDERS.put(SPLIT_MODE.PERCENTAGE, (req, participants, scale) -> {
            PercentageSplitRequest pReq = (PercentageSplitRequest) req;
            List<UserBps> items = pReq.getUserPercentageSplitDtoList().stream()
                    .map(u -> new UserBps(u.getUserId().toString(), u.getPercentage().movePointRight(2).intValueExact()))
                    .collect(Collectors.toList());
            return new PercentPayload(items);
        });
        BUILDERS.put(SPLIT_MODE.RATIO, (req, participants, scale) -> {
            RatioSplitRequest rReq = (RatioSplitRequest) req;
            List<UserRatio> items = rReq.getUserRatioSplitDtoList().stream()
                    .map(u -> new UserRatio(u.getUserId().toString(), u.getRatio()))
                    .collect(Collectors.toList());
            return new RatioPayload(items);
        });
    }

    public static SplitPayload build(SPLIT_MODE mode, SplitRequest request, List<String> participants, int scale) {
        PayloadBuilder builder = BUILDERS.get(mode);
        if (builder == null) {
            throw new IllegalArgumentException("Unsupported split mode: " + mode);
        }
        return builder.build(request, participants, scale);
    }
}
