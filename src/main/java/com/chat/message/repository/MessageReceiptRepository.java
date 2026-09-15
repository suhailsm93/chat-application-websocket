package com.chat.message.repository;

import com.chat.message.model.MessageReceiptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageReceiptRepository extends JpaRepository<MessageReceiptEntity, MessageReceiptEntity.MessageReceiptId> {
}