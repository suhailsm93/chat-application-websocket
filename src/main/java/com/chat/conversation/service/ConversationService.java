package com.chat.conversation.service;

import com.chat.common.exception.BadRequestException;
import com.chat.common.exception.ResourceNotFoundException;
import com.chat.conversation.model.ConversationEntity;
import com.chat.conversation.model.ConversationMember;
import com.chat.conversation.repository.ConversationMemberRepository;
import com.chat.conversation.repository.ConversationRepository;
import com.chat.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UserRepository userRepository;

    @Transactional
    public ConversationEntity createDirectConversation(UUID currentUserId, UUID targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw new BadRequestException("Cannot start a conversation with yourself");
        }
        if (!userRepository.existsById(targetUserId)) {
            throw new ResourceNotFoundException("Target user not found");
        }

        return conversationRepository.findDirectConversationBetweenUsers(currentUserId, targetUserId)
                .orElseGet(() -> {
                    ConversationEntity conv = ConversationEntity.builder()
                            .type(ConversationEntity.ConversationType.DIRECT)
                            .createdBy(currentUserId)
                            .build();
                    conv = conversationRepository.save(conv);

                    ConversationMember m1 = ConversationMember.builder()
                            .conversationId(conv.getId())
                            .userId(currentUserId)
                            .role(ConversationMember.MemberRole.ADMIN)
                            .build();

                    ConversationMember m2 = ConversationMember.builder()
                            .conversationId(conv.getId())
                            .userId(targetUserId)
                            .role(ConversationMember.MemberRole.ADMIN)
                            .build();

                    memberRepository.saveAll(List.of(m1, m2));
                    return conv;
                });
    }

    @Transactional
    public ConversationEntity createGroupConversation(UUID currentUserId, String title, List<UUID> memberIds) {
        ConversationEntity group = ConversationEntity.builder()
                .type(ConversationEntity.ConversationType.GROUP)
                .title(title)
                .createdBy(currentUserId)
                .build();
        group = conversationRepository.save(group);

        ConversationMember admin = ConversationMember.builder()
                .conversationId(group.getId())
                .userId(currentUserId)
                .role(ConversationMember.MemberRole.ADMIN)
                .build();
        memberRepository.save(admin);

        for (UUID memberId : memberIds) {
            if (!memberId.equals(currentUserId) && userRepository.existsById(memberId)) {
                ConversationMember member = ConversationMember.builder()
                        .conversationId(group.getId())
                        .userId(memberId)
                        .role(ConversationMember.MemberRole.MEMBER)
                        .build();
                memberRepository.save(member);
            }
        }

        return group;
    }
}