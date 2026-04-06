package com.fpoly.duan.service;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.SingularAttribute;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class DeleteGuardService {

    @PersistenceContext
    private EntityManager entityManager;

    public void assertNoReferences(Class<?> targetEntityClass, Object targetId) {
        if (targetEntityClass == null || targetId == null) {
            return;
        }

        String idField = resolveIdFieldName(targetEntityClass);
        Map<String, Long> refCounters = new HashMap<>();

        for (EntityType<?> entityType : entityManager.getMetamodel().getEntities()) {
            for (SingularAttribute<?, ?> attr : entityType.getSingularAttributes()) {
                if (!attr.isAssociation()) {
                    continue;
                }
                Class<?> associatedType = attr.getBindableJavaType();
                if (!targetEntityClass.equals(associatedType)) {
                    continue;
                }

                String jpql = "select count(e) from " + entityType.getName()
                        + " e where e." + attr.getName() + "." + idField + " = :id";
                Long count = entityManager.createQuery(jpql, Long.class)
                        .setParameter("id", targetId)
                        .getSingleResult();

                if (count != null && count > 0) {
                    refCounters.put(entityType.getName(), count);
                }
            }
        }

        if (!refCounters.isEmpty()) {
            log.warn("Block delete {}#{} because references exist: {}", targetEntityClass.getSimpleName(), targetId,
                    refCounters);
            String details = refCounters.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                    .map(e -> e.getKey() + "(" + e.getValue() + ")")
                    .collect(Collectors.joining(", "));
            throw new RuntimeException("Không thể xóa " + targetEntityClass.getSimpleName()
                    + " vì đang được tham chiếu bởi: " + details);
        }
    }

    public Object resolveEntityId(Object entity) {
        if (entity == null) {
            return null;
        }
        String idField = resolveIdFieldName(entity.getClass());
        Field f = findField(entity.getClass(), idField);
        if (f == null) {
            return null;
        }
        try {
            f.setAccessible(true);
            return f.get(entity);
        } catch (IllegalAccessException ex) {
            throw new RuntimeException("Không đọc được khóa chính để kiểm tra xóa.", ex);
        }
    }

    private String resolveIdFieldName(Class<?> entityClass) {
        Class<?> cur = entityClass;
        while (cur != null && cur != Object.class) {
            for (Field f : cur.getDeclaredFields()) {
                if (f.getAnnotation(jakarta.persistence.Id.class) != null) {
                    return f.getName();
                }
            }
            cur = cur.getSuperclass();
        }
        throw new RuntimeException("Không tìm thấy khóa chính cho entity: " + entityClass.getSimpleName());
    }

    private Field findField(Class<?> entityClass, String fieldName) {
        Class<?> cur = entityClass;
        while (cur != null && cur != Object.class) {
            try {
                return cur.getDeclaredField(fieldName);
            } catch (NoSuchFieldException ex) {
                cur = cur.getSuperclass();
            }
        }
        return null;
    }
}
