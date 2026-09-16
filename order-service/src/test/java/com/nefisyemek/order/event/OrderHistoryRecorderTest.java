package com.nefisyemek.order.event;

import com.nefisyemek.order.domain.OrderHistoryEntry;
import com.nefisyemek.order.domain.OrderHistoryStep;
import com.nefisyemek.order.repository.OrderHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHistoryRecorderTest {

    @Mock
    private OrderHistoryRepository history;

    private OrderHistoryRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new OrderHistoryRecorder(history);
    }

    @Test
    void recordPersistsNewEvent() {
        when(history.existsByEventId("evt-1")).thenReturn(false);

        recorder.record(UUID.randomUUID(), OrderHistoryStep.PAYMENT_CHARGED, "ok", "evt-1");

        verify(history).save(any(OrderHistoryEntry.class));
    }

    @Test
    void recordSkipsDuplicateEventId() {
        when(history.existsByEventId("evt-1")).thenReturn(true);

        recorder.record(UUID.randomUUID(), OrderHistoryStep.PAYMENT_CHARGED, "ok", "evt-1");

        verify(history, never()).save(any());
    }
}
