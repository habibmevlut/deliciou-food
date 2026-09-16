package com.nefisyemek.payment.event;

import com.nefisyemek.payment.domain.ProcessedEvent;
import com.nefisyemek.payment.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotentConsumerTest {

    @Mock
    private ProcessedEventRepository processed;

    private IdempotentConsumer inbox;

    @BeforeEach
    void setUp() {
        inbox = new IdempotentConsumer(processed);
    }

    @Test
    void claimReturnsTrueWhenNew() {
        when(processed.existsById("cmd-1")).thenReturn(false);
        when(processed.saveAndFlush(any(ProcessedEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(inbox.claim("cmd-1", "ChargePaymentCommand", UUID.randomUUID())).isTrue();
        verify(processed).saveAndFlush(any(ProcessedEvent.class));
    }

    @Test
    void claimReturnsFalseWhenAlreadyProcessed() {
        when(processed.existsById("cmd-1")).thenReturn(true);

        assertThat(inbox.claim("cmd-1", "ChargePaymentCommand", UUID.randomUUID())).isFalse();
        verify(processed, never()).saveAndFlush(any());
    }

    @Test
    void claimReturnsFalseOnUniqueViolation() {
        when(processed.existsById("cmd-1")).thenReturn(false);
        when(processed.saveAndFlush(any(ProcessedEvent.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThat(inbox.claim("cmd-1", "ChargePaymentCommand", UUID.randomUUID())).isFalse();
    }
}
