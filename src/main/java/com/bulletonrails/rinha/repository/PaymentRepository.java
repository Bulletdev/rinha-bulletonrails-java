package com.bulletonrails.rinha.repository;

import com.bulletonrails.rinha.model.PaymentSummary;
import com.bulletonrails.rinha.model.ProcessorSummary;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public class PaymentRepository {

    private static final String KEY_DEF_REQ = "pay:d:req";

    private static final String KEY_DEF_AMT = "pay:d:amt";

    private static final String KEY_FALL_REQ = "pay:f:req";

    private static final String KEY_FALL_AMT = "pay:f:amt";

    private static final String KEY_RECORDS = "pay:rec";

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final StringRedisTemplate redis;

    public PaymentRepository(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public void recordDefaultPayment(
            UUID correlationId, BigDecimal amount, Instant timestamp) {
        long amtCents = amount.multiply(HUNDRED).longValue();
        String member = correlationId + "|" + amtCents + "|1";
        double score = timestamp.toEpochMilli();

        redis.executePipelined(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) {
                operations.opsForValue().increment(KEY_DEF_REQ);
                operations.opsForValue().increment(KEY_DEF_AMT, amtCents);
                operations.opsForZSet().add(KEY_RECORDS, member, score);
                return null;
            }
        });
    }

    public void recordFallbackPayment(
            UUID correlationId, BigDecimal amount, Instant timestamp) {
        long amtCents = amount.multiply(HUNDRED).longValue();
        String member = correlationId + "|" + amtCents + "|0";
        double score = timestamp.toEpochMilli();

        redis.executePipelined(new SessionCallback<List<Object>>() {
            @Override
            @SuppressWarnings("unchecked")
            public List<Object> execute(RedisOperations operations) {
                operations.opsForValue().increment(KEY_FALL_REQ);
                operations.opsForValue().increment(KEY_FALL_AMT, amtCents);
                operations.opsForZSet().add(KEY_RECORDS, member, score);
                return null;
            }
        });
    }

    public PaymentSummary getSummary() {
        List<Object> results = redis.executePipelined(
                new SessionCallback<List<Object>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<Object> execute(RedisOperations operations) {
                        operations.opsForValue().get(KEY_DEF_REQ);
                        operations.opsForValue().get(KEY_DEF_AMT);
                        operations.opsForValue().get(KEY_FALL_REQ);
                        operations.opsForValue().get(KEY_FALL_AMT);
                        return null;
                    }
                });

        long defReq = parseLong(results, 0);
        long defAmt = parseLong(results, 1);
        long fallReq = parseLong(results, 2);
        long fallAmt = parseLong(results, 3);

        return buildSummary(defReq, defAmt, fallReq, fallAmt);
    }

    public PaymentSummary getSummary(Instant from, Instant to) {
        Set<String> records = redis.opsForZSet().rangeByScore(
            KEY_RECORDS,
            from.toEpochMilli(),
            to.toEpochMilli()
        );

        long defReq = 0;
        long fallReq = 0;
        long defAmt = 0;
        long fallAmt = 0;

        if (records != null) {
            for (String member : records) {
                String[] parts = member.split("\\|", 3);
                if (parts.length == 3) {
                    long amtCents = Long.parseLong(parts[1]);
                    if ("1".equals(parts[2])) {
                        defReq++;
                        defAmt += amtCents;
                    } else {
                        fallReq++;
                        fallAmt += amtCents;
                    }
                }
            }
        }

        return buildSummary(defReq, defAmt, fallReq, fallAmt);
    }

    public void purge() {
        redis.delete(Arrays.asList(
            KEY_DEF_REQ, KEY_DEF_AMT,
            KEY_FALL_REQ, KEY_FALL_AMT,
            KEY_RECORDS
        ));
    }

    private long parseLong(List<Object> results, int index) {
        if (results == null || index >= results.size()) {
            return 0L;
        }
        Object val = results.get(index);
        if (val == null) {
            return 0L;
        }
        return Long.parseLong(val.toString());
    }

    private PaymentSummary buildSummary(
            long defReq, long defAmt, long fallReq, long fallAmt) {
        return new PaymentSummary(
            new ProcessorSummary(defReq,
                BigDecimal.valueOf(defAmt)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP)),
            new ProcessorSummary(fallReq,
                BigDecimal.valueOf(fallAmt)
                    .divide(HUNDRED, 2, RoundingMode.HALF_UP))
        );
    }
}
