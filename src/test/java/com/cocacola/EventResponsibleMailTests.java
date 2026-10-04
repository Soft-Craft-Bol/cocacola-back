package com.cocacola;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import com.cocacola.domain.service.NotificationMailer;
import com.cocacola.domain.repository.EmailGateway;
import com.cocacola.domain.repository.UserRepository;
import com.cocacola.domain.model.User;
import com.cocacola.domain.model.Notification;
import com.cocacola.domain.model.EmailMessage;
import com.cocacola.persistence.crud.EventOperationsRepository;
import com.cocacola.persistence.entity.EventOperationsEntity;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class EventResponsibleMailTests {
    @Test void notificationsGoToAssignedActiveUsersWithoutDuplicateRecipients() {
        var gateway = mock(EmailGateway.class);
        var operations = mock(EventOperationsRepository.class);
        var users = mock(UserRepository.class);
        var mailer = new NotificationMailer(gateway, operations, users);
        ReflectionTestUtils.setField(mailer, "enabled", true);
        ReflectionTestUtils.setField(mailer, "recipients", "global@example.com");
        ReflectionTestUtils.setField(mailer, "publicUrl", "http://localhost:5173");
        when(gateway.isConfigured()).thenReturn(true);
        var config = new EventOperationsEntity();
        config.setOrganizerUserId("one"); config.setManagerUserId("one");
        when(operations.findById("event")).thenReturn(Optional.of(config));
        when(users.findById("one")).thenReturn(Optional.of(User.builder().id("one").email("assigned@example.com").active(true).build()));
        mailer.send(Notification.builder().eventId("event").severity("success").title("Meta alcanzada").message("2 asistentes").build());
        var message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(gateway).send(message.capture());
        assertEquals("assigned@example.com", message.getValue().to());
        verify(users, times(1)).findById("one");
    }
}
