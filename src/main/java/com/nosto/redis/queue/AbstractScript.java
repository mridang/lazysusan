/*******************************************************************************
 * Copyright (c) 2018 Nosto Solutions Ltd All Rights Reserved.
 * <p>
 * This software is the confidential and proprietary information of
 * Nosto Solutions Ltd ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the agreement you entered into with
 * Nosto Solutions Ltd.
 ******************************************************************************/
package com.nosto.redis.queue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

abstract class AbstractScript {
    /**
     * Lua return true gets mapped to 1L
     */
    private static final Long TRUE_RESPONSE = 1L;

    /**
     * Adds a message to a queue.
     *
     * @param now Time now
     * @param invisiblePeriod When the message becomes visible if the tenant does not have earlier messages in queue.
     * @param queue The name of the queue.
     * @param tenantMessage The message to be added.
     * @return true if the message was added, false if it was a duplicate
     */
    public boolean enqueue(Instant now, Duration invisiblePeriod, String queue, TenantMessage tenantMessage) {
        return TRUE_RESPONSE.equals(call(Function.ENQUEUE,
                slot(tenantMessage.getTenant()),
                bytes(queue),
                bytes(now.toEpochMilli()),
                bytes(now.plus(invisiblePeriod).toEpochMilli()),
                bytes(tenantMessage.getTenant()),
                bytes(tenantMessage.getKey()),
                tenantMessage.getPayload()));
    }

    /**
     * Removes messages from a queue.
     *
     * Removes N messages but guarantees only one message per tenant is removed.
     * This allows easy rate-limit implementation while reducing Redis round-trips.
     *
     * Note that removed messages will become invisible but stay in Redis until
     * {@link #ack} is called.
     *
     * @param now Time now
     * @param queue The name of the queue.
     * @param maxKeys Maximum number of keys to remove.
     * @return A list of removed messages
     */
    public abstract List<TenantMessage> dequeue(Instant now, String queue, int maxKeys);

    /**
     * Acks that a message was processed and it can be permanently removed.
     *
     * @param queue The name of the queue.
     * @param tenant The tenant to whom the message belongs.
     * @param key The de-duplication key of the message to be acked.
     */
    public void ack(String queue, String tenant, String key) {
        call(Function.ACK, slot(tenant), bytes(queue), bytes(tenant), bytes(key));
    }

    Object call(Function function, byte[] key, byte[]... args) {
        List<byte[]> argsList = new ArrayList<>(2 + args.length);
        argsList.add(function.getName());
        argsList.add(key);
        argsList.addAll(Arrays.asList(args));
        return evalsha(Collections.singletonList(key), argsList);
    }

    abstract Object evalsha(final List<byte[]> keys, final List<byte[]> args);

    abstract byte[] slot(String tenant);

    static byte[] bytes(String string) {
        return string.getBytes(StandardCharsets.UTF_8);
    }

    static byte[] bytes(long l) {
        return Long.toString(l).getBytes(StandardCharsets.UTF_8);
    }

    static List<TenantMessage> unpack(List<byte[]> response) {
        ArrayList<TenantMessage> result = new ArrayList<>(response.size() >> 1);
        Iterator<byte[]> it = response.iterator();
        while (it.hasNext()) {
            result.add(new TenantMessage(new String(it.next()), new String(it.next()), it.next()));
        }
        return result;
    }

    enum Function {
        ENQUEUE("enqueue"),
        DEQUEUE("dequeue"),
        ACK("ack");

        private final byte[] name;

        Function(String name) {
            this.name = bytes(name);
        }

        public byte[] getName() {
            return name;
        }
    }

    public static class TenantMessage {
        private final String tenant;
        private final String key;
        private final byte[] payload;

        public TenantMessage(String tenant, String key, byte[] payload) {
            this.tenant = tenant;
            this.key = key;
            this.payload = payload;
        }

        public String getTenant() {
            return tenant;
        }

        public String getKey() {
            return key;
        }

        public byte[] getPayload() {
            return payload;
        }
    }
}
