package org.example.dao;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.example.models.TelegramUser;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Repository
public class TelegramUserDao {

    @PersistenceContext
    private EntityManager entityManager;

    public TelegramUser getById(Long id) {
        return entityManager.find(TelegramUser.class, id);
    }

    public TelegramUser getByTelegramUserId(Long telegramUserId) {
        return (TelegramUser) entityManager.createQuery("SELECT u FROM TelegramUser u WHERE u.telegramUserId = :telegramUserId")
                .setParameter("telegramUserId", telegramUserId)
                .getSingleResult();
    }

    public TelegramUser getByTelegramUserName(String telegramUserName) {
        return (TelegramUser) entityManager.createQuery("SELECT u FROM TelegramUser u WHERE u.telegramUserName = :telegramUserName")
                .setParameter("telegramUserName", telegramUserName)
                .getSingleResult();
    }

    public void update(TelegramUser telegramUser) {
        entityManager.merge(telegramUser);
    }

    public void deleteById(Long id) {
        TelegramUser telegramUser = getById(id);
        if (telegramUser != null) {
            entityManager.remove(telegramUser);
        }
    }

    public void deleteByTelegramUserId(Long telegramUserId) {
        TelegramUser telegramUser = getByTelegramUserId(telegramUserId);
        if (telegramUser != null) {
            entityManager.remove(telegramUser);
        }
    }

    public boolean existsById(Long id) {
        return entityManager.find(TelegramUser.class, id) != null;
    }

    public boolean existsByTelegramUserId(Long telegramUserId) {
        Long count = (Long) entityManager.createQuery("SELECT COUNT(u) FROM TelegramUser u WHERE u.telegramUserId = :telegramUserId")
                .setParameter("telegramUserId", telegramUserId)
                .getSingleResult();
        return count > 0;
    }

    public boolean existsByTelegramUserName(Long telegramUserName) {
        Long count = (Long) entityManager.createQuery("SELECT COUNT(u) FROM TelegramUser u WHERE u.telegramUserName = :telegramUserName")
                .setParameter("telegramUserName", telegramUserName)
                .getSingleResult();
        return count > 0;
    }
}