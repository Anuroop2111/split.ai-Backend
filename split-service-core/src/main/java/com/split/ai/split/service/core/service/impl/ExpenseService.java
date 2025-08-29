package com.split.ai.split.service.core.service.impl;

import com.split.ai.split.service.commons.exception.ErrorCode;
import com.split.ai.split.service.commons.exception.SplitException;
import com.split.ai.split.service.core.helper.ExpenseHelper;
import com.split.ai.split.service.core.mapper.ExpenseServiceMapper;
import com.split.ai.split.service.core.service.IExpenseService;
import com.split.ai.split.service.core.engine.SplitEngineAdapter;
import com.split.ai.split.service.core.utils.MoneyUtil;
import com.split.ai.split.service.model.enums.EXPENSE_REVISION_STATUS;
import com.split.ai.split.service.model.enums.EXPENSE_STATUS;
import com.split.ai.split.service.model.enums.CURRENCY;
import com.split.ai.split.service.model.request.expense.CreateExpenseRequest;
import com.split.ai.split.service.model.request.expense.DeleteExpenseRequest;
import com.split.ai.split.service.model.request.expense.UpdateExpenseRequest;
import com.split.ai.split.service.model.response.expense.ChangeDto;
import com.split.ai.split.service.model.response.expense.ExpenseEditDto;
import com.split.ai.split.service.model.response.expense.ExpenseHistoryResponse;
import com.split.ai.split.service.model.response.expense.ExpenseResponse;
import com.split.ai.split.service.repository.dao.IExpenseDao;
import com.split.ai.split.service.repository.dao.IExpenseRevisionDao;
import com.split.ai.split.service.repository.dao.IUserDao;
import com.split.ai.split.service.repository.entity.ExpenseEntity;
import com.split.ai.split.service.repository.entity.ExpenseRevisionEntity;
import com.split.ai.split.service.repository.entity.UserEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.tuple.Pair;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import io.split.engine.Share;
import io.split.engine.SplitEngineJvm;
import io.split.engine.SplitInput;

/**
 * Service handling expense operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExpenseService implements IExpenseService {

    private final IExpenseDao expenseDao;
    private final IExpenseRevisionDao expenseRevisionDao;
    private final IUserDao userDao;

    @Override
    public ExpenseResponse getExpense(UUID expenseId) {
        log.info("[ExpenseService : getExpense] : {}", expenseId);
        ExpenseEntity entity = expenseDao.findById(expenseId);
        return ExpenseServiceMapper.MAPPER.toExpenseResponse(entity);
    }

    @Override
    public void createExpense(CreateExpenseRequest request) {
        log.info("[ExpenseService : createExpense] : {}", request);
        validateSplit(request);

        UUID expenseId = UUID.randomUUID();
        ExpenseRevisionEntity revision = ExpenseServiceMapper.MAPPER.toRevisionEntity(request, expenseId);
        expenseRevisionDao.saveRevision(revision);
        ExpenseEntity entity = ExpenseServiceMapper.MAPPER.toExpenseEntity(expenseId, request, revision);
        expenseDao.save(entity);
    }

    @Override
    public void updateExpense(UpdateExpenseRequest request) {
        log.info("[ExpenseService : updateExpense] : {}", request);

        ExpenseEntity existing = expenseDao.findById(request.getExpenseId());
        if (existing == null) {
            log.error("[ExpenseService : updateExpense] : expense {} not found", request.getExpenseId());
            throw SplitException.createException(ErrorCode.ENTITY_NOT_FOUND);
        }

        ExpenseRevisionEntity currentRevision = existing.getCurrentRevision();
        Pair<Boolean, ExpenseRevisionEntity> updatedFlagAndUpdatedExpenseRevision = ExpenseHelper.getUpdatedExpenseRevision(request, currentRevision);
        Boolean updated = updatedFlagAndUpdatedExpenseRevision.getLeft();
        ExpenseRevisionEntity newRevision = updatedFlagAndUpdatedExpenseRevision.getRight();

        if (!updated) {
            log.error("[ExpenseService : updateExpense] : invalid update {}", request);
            throw SplitException.createException(ErrorCode.INVALID_EXPENSE_UPDATE);
        }

        currentRevision.setRevisionStatus(EXPENSE_REVISION_STATUS.IN_ACTIVE);
        expenseRevisionDao.updateRevision(currentRevision);
        expenseRevisionDao.saveRevision(newRevision);
        existing.setCurrentRevision(newRevision);
        existing.setCurrentRevisionId(newRevision.getExpenseRevisionId());
        expenseDao.update(existing);
    }

    @Override
    public void deleteExpense(DeleteExpenseRequest request) {
        log.info("[ExpenseService : deleteExpense] : {}", request);
        ExpenseEntity existing = expenseDao.findById(request.getExpenseId());
        if (existing == null) {
            return;
        }
        ExpenseRevisionEntity currentRevision = existing.getCurrentRevision();
        currentRevision.setRevisionStatus(EXPENSE_REVISION_STATUS.IN_ACTIVE);
        expenseRevisionDao.updateRevision(currentRevision);
        existing.setExpenseStatus(EXPENSE_STATUS.CANCELLED);
        existing.setCurrentRevision(currentRevision);
        expenseDao.update(existing);
    }

    @Override
    public ExpenseHistoryResponse getHistory(UUID expenseId) {
        log.info("[ExpenseService : getHistory] : {}", expenseId);
        List<ExpenseRevisionEntity> revisions = expenseDao.findRevisions(expenseId);
        List<ExpenseEditDto> edits = new ArrayList<>();

        for (int i = 0; i < revisions.size(); i++) {
            ExpenseRevisionEntity current = revisions.get(i);
            ExpenseRevisionEntity previous = i + 1 < revisions.size() ? revisions.get(i + 1) : null;

            Map<String, ChangeDto> changes = new HashMap<>();
            Map<String, ChangeDto> userShareChanges = new HashMap<>();

            computeChange("payer", previous == null ? null : previous.getPayerId(), current.getPayerId(), changes);
            computeChange("amount", previous == null ? null : previous.getAmount(), current.getAmount(), changes);
            computeChange("expenseDate", previous == null ? null : previous.getExpenseDate(), current.getExpenseDate(), changes);
            computeChange("splitMode", previous == null ? null : previous.getSplitMode(), current.getSplitMode(), changes);
            computeChange("currency", previous == null ? null : previous.getCurrency(), current.getCurrency(), changes);
            computeChange("category", previous == null ? null : previous.getCategory(), current.getCategory(), changes);
            computeChange("subCategory", previous == null ? null : previous.getSubCategory(), current.getSubCategory(), changes);
            computeChange("description", previous == null ? null : previous.getDescription(), current.getDescription(), changes);
            computeChange("metaData", previous == null ? null : previous.getMetaData(), current.getMetaData(), changes);

            computeUserShareChanges(previous == null ? null : previous.getUserShares(), current.getUserShares(), userShareChanges);

            ExpenseEditDto dto = ExpenseEditDto.builder()
                    .editedBy(getUserFullName(current.getEditedUserId()))
                    .editedAt(current.getCreatedAt())
                    .changes(changes.isEmpty() ? null : changes)
                    .userShareChanges(userShareChanges.isEmpty() ? null : userShareChanges)
                    .build();
            edits.add(dto);
        }

        return ExpenseHistoryResponse.builder()
                .expenseEditList(edits)
                .build();
    }

    private void validateSplit(CreateExpenseRequest request) {
        int scale = resolveScale(request.getCurrency());
        SplitInput input = SplitEngineAdapter.toInput(request, scale);
        List<Share> shares = SplitEngineJvm.computeShares(input);

        Map<String, BigDecimal> expectedMap = shares.stream()
                .collect(Collectors.toMap(Share::getUserId, s -> MoneyUtil.toBig(s.getOwe())));
        Map<String, BigDecimal> clientMap = request.getUserExpenseDetails().stream()
                .collect(Collectors.toMap(dto -> dto.getUserId().toString(), dto -> dto.getSharedAmount().setScale(scale, RoundingMode.UNNECESSARY)));

        if (!expectedMap.keySet().equals(clientMap.keySet())) {
            log.error("[ExpenseService : validateSplit] : participant mismatch expected {} got {}", expectedMap.keySet(), clientMap.keySet());
            throw SplitException.createException(ErrorCode.INVALID_QUERY);
        }

        for (String user : expectedMap.keySet()) {
            BigDecimal expected = expectedMap.get(user).setScale(scale, RoundingMode.UNNECESSARY);
            BigDecimal actual = clientMap.get(user);
            if (expected.compareTo(actual) != 0) {
                log.error("[ExpenseService : validateSplit] : amount mismatch for user {} expected {} got {}", user, expected, actual);
                throw SplitException.createException(ErrorCode.INVALID_QUERY);
            }
        }
    }

    private int resolveScale(CURRENCY currency) {
        return currency == null ? 2 : currency.scale();
    }

    private void computeUserShareChanges(Map<UUID, BigDecimal> oldShares, Map<UUID, BigDecimal> newShares, Map<String, ChangeDto> userShareChanges) {
        if (newShares != null) {
            for (Map.Entry<UUID, BigDecimal> entry : newShares.entrySet()) {
                UUID userId = entry.getKey();
                BigDecimal newVal = entry.getValue();
                BigDecimal oldVal = oldShares == null ? null : oldShares.get(userId);
                if (oldVal == null || newVal.compareTo(oldVal) != 0) {
                    String name = getUserFullName(userId);
                    String oldStr = oldVal == null ? "0" : convertToString(oldVal);
                    String newStr = newVal == null ? "0" : convertToString(newVal);
                    userShareChanges.put(name, ChangeDto.builder().oldValue(oldStr).newValue(newStr).build());
                }
            }
        }
        if (oldShares != null) {
            for (Map.Entry<UUID, BigDecimal> entry : oldShares.entrySet()) {
                UUID userId = entry.getKey();
                if (newShares == null || !newShares.containsKey(userId)) {
                    String name = getUserFullName(userId);
                    String oldStr = convertToString(entry.getValue());
                    userShareChanges.put(name, ChangeDto.builder().oldValue(oldStr).newValue("0").build());
                }
            }
        }
    }

    private String getUserFullName(UUID userId) {
        UserEntity user = userDao.findById(userId);
        return user != null ? user.getFullName() : userId.toString();
    }

    private void computeChange(String key, Object oldVal, Object newVal, Map<String, ChangeDto> changes) {
        if (!Objects.equals(oldVal, newVal)) {
            String oldStr = oldVal == null ? null : convertToString(oldVal);
            String newStr = newVal == null ? null : convertToString(newVal);
            changes.put(key, ChangeDto.builder().oldValue(oldStr).newValue(newStr).build());
        }
    }

    private String convertToString(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd.toPlainString();
        }
        return value.toString();
    }
}
