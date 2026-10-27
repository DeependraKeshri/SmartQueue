package com.smartqueue.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * WebSocket security:
 *  - CONNECT: optional Bearer token -> authenticated principal. Invalid token = rejected.
 *  - SUBSCRIBE: public topics are open; business topics require staff of THAT business; /user queues require login.
 *  - SEND: clients may not publish anything (server push only).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {
    private final TokenAuthenticator authenticator;

    public StompAuthChannelInterceptor(TokenAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (acc == null || acc.getCommand() == null) {
            return message;
        }
        StompCommand cmd = acc.getCommand();
        if (StompCommand.CONNECT.equals(cmd)) {
            String header = acc.getFirstNativeHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                UserPrincipal p = authenticator.authenticate(header.substring(7).trim())
                        .orElseThrow(() -> new MessageDeliveryException("Invalid or expired token"));
                acc.setUser(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
            }
        } else if (StompCommand.SUBSCRIBE.equals(cmd)) {
            authorizeSubscribe(acc.getDestination(), acc.getUser());
        } else if (StompCommand.SEND.equals(cmd)) {
            throw new MessageDeliveryException("Clients are not allowed to publish messages");
        }
        return message;
    }

    static void authorizeSubscribe(String destination, Principal user) {
        if (destination == null) {
            throw new MessageDeliveryException("Missing destination");
        }
        if (destination.startsWith("/topic/public/")) {
            return;
        }
        UserPrincipal p = principalOf(user);
        if (destination.startsWith("/user/queue/")) {
            if (p == null) {
                throw new MessageDeliveryException("Authentication required");
            }
            return;
        }
        if (destination.startsWith("/topic/business/")) {
            String[] parts = destination.split("/");   // "", topic, business, {id}, kind
            boolean queueEvents = parts.length >= 5 && "queues".equals(parts[4]);
            boolean roleOk = p != null && (p.getRole().isBusinessStaff()
                    || (queueEvents && p.getRole() == com.smartqueue.entity.Role.DISPLAY));   // display screens only get queue events
            if (!roleOk || parts.length < 5 || p.getBusinessId() == null
                    || !String.valueOf(p.getBusinessId()).equals(parts[3])) {
                throw new MessageDeliveryException("Not authorized for this destination");
            }
            return;
        }
        if (destination.startsWith("/topic/display/")) {
            String[] parts = destination.split("/");
            if (p == null || (p.getRole() != com.smartqueue.entity.Role.DISPLAY && !p.getRole().isBusinessStaff())
                    || parts.length < 4 || p.getBusinessId() == null || !String.valueOf(p.getBusinessId()).equals(parts[3])) {
                throw new MessageDeliveryException("Not authorized for this destination");
            }
            return;
        }
        throw new MessageDeliveryException("Unknown destination");
    }

    private static UserPrincipal principalOf(Principal user) {
        if (user instanceof Authentication a && a.getPrincipal() instanceof UserPrincipal p) {
            return p;
        }
        return null;
    }
}
